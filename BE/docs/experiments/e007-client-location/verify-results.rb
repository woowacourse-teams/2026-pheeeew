# frozen_string_literal: true
# 독립 CSV 재계산용이에요. Ruby 표준 라이브러리만 사용하고 파일을 변경하지 않아요.
require 'csv'
require 'zlib'
require 'digest'
require 'set'

root = ARGV.fetch(0)
%w[run1 run2].each do |run|
  File.readlines("#{root}/#{run}/checksums.sha256").each do |line|
    hash, name = line.split
    raise 'hash mismatch' unless Digest::SHA256.file("#{root}/#{run}/#{name}").hexdigest == hash
  end
end
raise 'runs differ' unless File.binread("#{root}/run1/checksums.sha256") == File.binread("#{root}/run2/checksums.sha256")
params = File.readlines("#{root}/run2/parameters.txt").to_h { |l| k, v = l.strip.split('='); [k, v.to_f] }
groups = Hash.new { |h, k| h[k] = [] }
truth = {}
ids = Set.new
Zlib::GzipReader.open("#{root}/run2/coordinates.csv.gz") do |gz|
  CSV.new(gz, headers: true).each do |r|
    key = r.values_at('scene', 'seed', 'index')
    raise 'duplicate' unless ids.add?(key + [r['model']])
    tx, ty, gx, gy, x, y, e = r.values_at('true_x', 'true_y', 'grid_x', 'grid_y', 'shown_x', 'shown_y', 'error_m').map(&:to_f)
    raise 'nonfinite' unless [tx, ty, gx, gy, x, y, e].all?(&:finite?)
    raise 'inputs differ' if truth.key?(key) && truth[key] != [tx, ty]
    truth[key] = [tx, ty]
    raise 'snap' unless gx == ((tx + 150) / 300).floor * 300 && gy == ((ty + 150) / 300).floor * 300
    raise 'error' unless (Math.hypot(x - tx, y - ty) - e).abs < 1e-9
    raise 'D support' if r['model'] == 'D' && Math.hypot(x - gx, y - gy) >= 300
    raise 'clipped' unless [x.abs, y.abs].max + 8.4 < params['common_plot_bound_m']
    groups[r.values_at('scene', 'model', 'seed')] << [e, x, y]
    groups[r.values_at('scene', 'model') + ['pooled']] << [e, x, y]
  end
end
raise 'visual count' unless ids.size == 298500 && truth.size == 99500
quantile = ->(a, p) { a.sort[(p * a.size).ceil - 1] }
metrics = CSV.read("#{root}/run2/metrics.csv", headers: true)
raise 'metric count' unless metrics.size == 72 && groups.size == 72
metrics.each do |r|
  a = groups.fetch(r.values_at('scene', 'model', 'seed'))
  n = a.size.to_f
  errors = a.map(&:first)
  base = r['scene'].split('-').last.to_i
  expected = r['seed'] == 'pooled' ? base : base / 5
  raise 'group count' unless a.size == expected && r['n'].to_i == expected
  sums = [0.0] * 4
  a.each do |_e, x, y|
    [Math.cos(2 * Math::PI * x / 300), Math.sin(2 * Math::PI * x / 300),
     Math.cos(2 * Math::PI * y / 300), Math.sin(2 * Math::PI * y / 300)].each_with_index { |v, i| sums[i] += v }
  end
  values = { 'rms_m' => Math.sqrt(errors.sum { |e| e * e } / n),
             'p50_m' => quantile.call(errors, 0.5), 'p95_m' => quantile.call(errors, 0.95),
             'p99_m' => quantile.call(errors, 0.99), 'max_m' => errors.max,
             'outside300_fraction' => errors.count { |e| e > 300 } / n,
             'period300_amplitude' => (Math.hypot(*sums[0, 2]) + Math.hypot(*sums[2, 2])) / (2 * n) }
  values.each { |key, value| raise "metric #{key}" unless (r[key].to_f - value).abs < 1e-8 }
end
attacks = Hash.new { |h, k| h[k] = [] }
prefix_ids = Set.new
Zlib::GzipReader.open("#{root}/run2/attack-prefixes.csv.gz") do |gz|
  CSV.new(gz, headers: true).each do |r|
    raise 'attack duplicate' unless prefix_ids.add?(r.values_at('anchor', 'model', 'seed', 'trajectory', 'n'))
    x, y, mx, my, e = r.values_at('true_x', 'true_y', 'mean_x', 'mean_y', 'error_m').map(&:to_f)
    raise 'anchor' unless [x, y] == (r['anchor'] == '0' ? [120, -90] : [0, 0])
    raise 'prefix error' unless (Math.hypot(mx - x, my - y) - e).abs < 1e-9
    attacks[r.values_at('anchor', 'model', 'n')] << e
  end
end
raise 'attack count' unless prefix_ids.size == 21000 && attacks.size == 42
rows = CSV.read("#{root}/run2/attack-metrics.csv", headers: true)
raise 'attack metric count' unless rows.size == 42
rows.each do |r|
  a = attacks.fetch(r.values_at('anchor', 'model', 'n'))
  raise 'trajectory count' unless a.size == 500 && r['trajectories'].to_i == 500
  theory = Math.sqrt(r['model'] == 'D' ? (r['anchor'] == '0' ? 22500 : 0) + params['d_kernel_m2'] / r['n'].to_i : params['target_m2'] / r['n'].to_i)
  values = { 'rms_m' => Math.sqrt(a.sum { |e| e * e } / 500), 'p50_m' => quantile.call(a, 0.5),
             'p95_m' => quantile.call(a, 0.95), 'within25_fraction' => a.count { |e| e <= 25 } / 500.0,
             'theoretical_rms_m' => theory }
  values.each { |key, value| raise "attack metric #{key}" unless (value - r[key].to_f).abs < 1e-8 }
end
images = Dir["#{root}/run2/*.png"]
raise 'PNG count' unless images.size == 5
images.each do |p|
  expected = p.end_with?('repeated-observation.png') ? [2100, 860] : [3264, 1240]
  raise 'PNG dimensions' unless File.binread(p)[16, 8].unpack('NN') == expected
end
puts 'PASS: both-run hashes; 298500 unique visual points; 99500 identical paired inputs; 72 visual metric rows; 21000 prefix rows; 42 attack metric rows; support/grid/finite/plot bounds; 5 PNG dimensions'
