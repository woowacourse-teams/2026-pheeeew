# frozen_string_literal: true
# 실제 실행의 두 원본을 읽어 보존 입력·통계와 독립 대조해요. 파일을 변경하지 않아요.
require 'csv'
require 'digest'
require 'set'
require 'zlib'

root = ARGV.fetch(0)
sources = File.readlines("#{root}/source-before.sha256")
sources.each do |line|
  hash, path = line.split
  raise "source hash: #{path}" unless Digest::SHA256.file(path).hexdigest == hash
end
%w[run1 run2].each do |run|
  manifest = File.readlines("#{root}/#{run}/checksums.sha256")
  raise 'output count' unless manifest.size == 5
  manifest.each do |line|
    hash, name = line.split
    raise "output hash: #{name}" unless Digest::SHA256.file("#{root}/#{run}/#{name}").hexdigest == hash
  end
end
raise 'runs differ' unless File.binread("#{root}/run1/checksums.sha256") == File.binread("#{root}/run2/checksums.sha256")

included = ->(scene, index) { %w[stationary-5000 hotspots-4500 uniform-45000].include?(scene) && (scene != 'uniform-45000' || index.to_i < 900) }
truths = {}
Zlib::GzipReader.open('docs/experiments/e007-client-location/results/2026-09-07/raw/coordinates.csv.gz') do |gz|
  CSV.new(gz, headers: true).each do |r|
    next unless r['model'] == 'D' && included.call(r['scene'], r['index'])
    key = r.values_at('scene', 'seed', 'index')
    raise 'duplicate input' if truths.key?(key)
    truths[key] = r.values_at('true_x', 'true_y', 'grid_x', 'grid_y').map { |v| Float(v) }
  end
end
references = {}
Zlib::GzipReader.open('docs/experiments/e009-location-pipeline/results/2026-09-07/raw/coordinates.csv.gz') do |gz|
  CSV.new(gz, headers: true).each do |r|
    next unless %w[1 2].include?(r['mode']) && included.call(r['scene'], r['index'])
    key = r.values_at('scene', 'seed', 'index', 'mode')
    raise 'duplicate reference' if references.key?(key)
    references[key] = r.values_at('sent_x', 'sent_y', 'shown_x', 'shown_y').map { |v| Float(v) }
  end
end
raise 'input counts' unless truths.size == 14000 && references.size == 28000
input_groups = truths.keys.group_by { |scene, seed, _index| [scene, seed] }
raise 'input groups' unless input_groups.size == 15
%w[stationary-5000 hotspots-4500 uniform-45000].each do |scene|
  (2026090701..2026090705).each do |seed|
    expected = scene == 'stationary-5000' ? 1000 : 900
    indices = input_groups.fetch([scene, seed.to_s]).map { |key| Integer(key[2]) }.sort
    raise 'seed indices' unless indices == (0...expected).to_a
  end
end
distance = ->(a, b) { Math.hypot(a[0] - b[0], a[1] - b[1]) }
ids = Set.new
groups = Hash.new { |h, k| h[k] = [] }
Zlib::GzipReader.open("#{root}/run2/coordinates.csv.gz") do |gz|
  CSV.new(gz, headers: true).each do |r|
    identity = r.values_at('scene', 'seed', 'index')
    mode = r['mode']
    raise 'mode' unless %w[0 1 2].include?(mode)
    raise 'duplicate output' unless ids.add?(identity + [mode])
    source = truths.fetch(identity)
    actual_truth = r.values_at('true_x', 'true_y').map { |v| Float(v) }
    sent = r.values_at('sent_x', 'sent_y').map { |v| Float(v) }
    shown = r.values_at('shown_x', 'shown_y').map { |v| Float(v) }
    longitude, latitude, error, ref_error = r.values_at('longitude', 'latitude', 'error_m', 'reference_error_m').map { |v| Float(v) }
    raise 'nonfinite' unless (actual_truth + sent + shown + [longitude, latitude, error, ref_error]).all?(&:finite?)
    raise 'truth changed' unless actual_truth == source[0, 2]
    raise 'WGS range' unless longitude.between?(-180, 180) && latitude.between?(-90, 90)
    raise 'error value' unless (distance.call(shown, actual_truth) - error).abs < 1e-8
    if mode == '0'
      raise 'old grid' unless sent == source[2, 2]
      raise 'old square' unless shown.zip(sent).all? { |a, b| (a - b).abs <= 150.0001 }
      raise 'old reference sentinel' unless ref_error.zero?
    else
      reference = references.fetch(identity + [mode])
      raise 'sent changed' unless distance.call(sent, reference[0, 2]) < 0.0001
      difference = distance.call(shown, reference[2, 2])
      raise 'E009 discrepancy' unless difference < 0.0001 && (difference - ref_error).abs < 1e-8
      raise 'server support' unless distance.call(shown, sent) <= 300.0001
      raise 'true location bound' unless error <= (mode == '1' ? 300.0001 : 600.0001)
      raise 'client support' unless distance.call(sent, actual_truth) <= 300.0001
    end
    px = ((shown[0] + 1200) * 1000 / 2400).round
    py = ((1200 - shown[1]) * 1000 / 2400).round
    raise 'clipping' unless px.between?(3, 996) && py.between?(3, 996)
    groups[[r['scene'], mode]] << [error, ref_error]
  end
end
raise 'output counts' unless ids.size == 42000 && groups.size == 9
rows = CSV.read("#{root}/run2/metrics.csv", headers: true)
raise 'metric count' unless rows.size == 9
metric_ids = Set.new
rows.each do |r|
  key = r.values_at('scene', 'mode')
  raise 'duplicate metric' unless metric_ids.add?(key)
  values = groups.fetch(key)
  expected_n = r['scene'] == 'stationary-5000' ? 5000 : 4500
  raise 'scene count' unless values.size == expected_n
  errors = values.map(&:first)
  expected = { 'n' => expected_n, 'rms_error_m' => Math.sqrt(errors.sum { |v| v * v } / expected_n),
               'max_error_m' => errors.max, 'outside300_fraction' => errors.count { |e| e > 300 }.fdiv(expected_n),
               'max_reference_error_m' => values.map(&:last).max }
  expected.each { |key, value| raise "metric #{key}" unless (Float(r[key]) - value).abs < 1e-8 }
end
pngs = Dir["#{root}/run2/*.png"]
raise 'PNG count' unless pngs.size == 3
pngs.each { |p| raise 'PNG size' unless File.binread(p)[16, 8].unpack('NN') == [3264, 1240] }
puts 'PASS: source hashes; both five-file hashes; 14000 paired input events; 42000 output rows; old square and new disks bounded; new modes match E009 within0.0001m; nine metrics; three PNG dimensions; no clipping'
