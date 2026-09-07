# frozen_string_literal: true
# E007 보존과 이중 랜덤 경로를 원본 CSV에서 독립 대조해요. 쓰기는 하지 않아요.
require 'csv'
require 'zlib'
require 'digest'
require 'set'

input, root = ARGV
raise 'E007 입력과 E009 실행 폴더가 필요해요.' unless input && root
%w[run1 run2].each do |run|
  lines = File.readlines("#{root}/#{run}/checksums.sha256")
  raise 'file count' unless lines.size == 6
  lines.each do |line|
    hash, name = line.split
    raise "hash #{name}" unless Digest::SHA256.file("#{root}/#{run}/#{name}").hexdigest == hash
  end
end
raise 'runs differ' unless File.binread("#{root}/run1/checksums.sha256") == File.binread("#{root}/run2/checksums.sha256")
source = {}
Zlib::GzipReader.open(input) do |gz|
  CSV.new(gz, headers: true).each do |r|
    next unless r['model'] == 'D'
    key = r.values_at('scene', 'seed', 'index')
    raise 'duplicate source' if source.key?(key)
    source[key] = r.values_at('true_x', 'true_y', 'grid_x', 'grid_y', 'shown_x', 'shown_y').map { |v| Float(v) }
  end
end
raise 'source count' unless source.size == 99500
ids = Set.new
offsets = {}
groups = Hash.new { |h, k| h[k] = [] }
different = 0
Zlib::GzipReader.open("#{root}/run2/coordinates.csv.gz") do |gz|
  CSV.new(gz, headers: true).each do |r|
    key = r.values_at('scene', 'seed', 'index')
    mode = Integer(r['mode'])
    raise 'duplicate output' unless ids.add?(key + [mode])
    raise 'mode' unless [1, 2, 3].include?(mode)
    original = source.fetch(key)
    truth = r.values_at('true_x', 'true_y').map { |v| Float(v) }
    sent = r.values_at('sent_x', 'sent_y').map { |v| Float(v) }
    shown = r.values_at('shown_x', 'shown_y').map { |v| Float(v) }
    raise 'source truth altered' unless truth == original[0, 2]
    raise 'age style' unless Float(r['age_hours']).zero? && Float(r['alpha']) == 1
    error = Math.hypot(shown[0] - truth[0], shown[1] - truth[1])
    raise 'error' unless (error - Float(r['error_m'])).abs < 1e-9
    if mode == 1
      raise 'mode 1' unless sent == truth && error <= 300 + 1e-9
      offsets[key] = shown.zip(sent).map { |a, b| a - b }
    elsif mode == 2
      client = sent.zip(truth).map { |a, b| a - b }
      server = shown.zip(sent).map { |a, b| a - b }
      raise 'stage radius' unless Math.hypot(*client) <= 300 + 1e-9 && Math.hypot(*server) <= 300 + 1e-9 && error <= 600 + 1e-9
      raise 'server offset' unless server.zip(offsets.fetch(key)).all? { |a, b| (a - b).abs < 1e-9 }
      different += 1 if Math.hypot(*client) > 1e-9
    else
      raise 'D changed' unless sent == original[2, 2] && shown == original[4, 2]
      raise 'D radius' unless Math.hypot(shown[0] - sent[0], shown[1] - sent[1]) <= 300 + 1e-9
    end
    groups[[r['scene'], mode]] << error
  end
end
raise 'rows' unless ids.size == 298500 && different == 99500 && groups.size == 12
rows = CSV.read("#{root}/run2/metrics.csv", headers: true)
raise 'metric rows' unless rows.size == 12
metric_ids = Set.new
rows.each do |r|
  key = [r['scene'], Integer(r['mode'])]
  raise 'duplicate metric' unless metric_ids.add?(key)
  errors = groups.fetch(key)
  values = { 'n' => errors.size, 'mean_error_m' => errors.sum / errors.size,
             'rms_error_m' => Math.sqrt(errors.sum { |e| e * e } / errors.size),
             'max_error_m' => errors.max, 'outside300_fraction' => errors.count { |e| e > 300 }.fdiv(errors.size) }
  values.each { |k, v| raise "metric #{key} #{k}" unless (Float(r[k]) - v).abs < 1e-6 }
end
pngs = Dir["#{root}/run2/*.png"]
raise 'PNG count' unless pngs.size == 4
pngs.each { |p| raise 'PNG dimensions' unless File.binread(p)[16, 8].unpack('NN') == [3264, 1280] }
puts 'PASS: two runs / six hashes; 99500 source events; 298500 route rows; mode 2 has two bounded stages and same server offset as mode 1; D preserved; 12 metrics recomputed; four PNG dimensions'
