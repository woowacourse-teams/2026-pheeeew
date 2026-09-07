# frozen_string_literal: true
# E007 원본과 E008 수치를 독립적으로 대조해요. 파일 쓰기는 하지 않아요.
require 'csv'
require 'zlib'
require 'digest'
require 'set'

input, root = ARGV
raise 'E007 입력과 E008 실행 폴더를 지정해요.' unless input && root
%w[run1 run2].each do |run|
  manifest = File.readlines("#{root}/#{run}/checksums.sha256")
  raise 'file count' unless manifest.size == 45
  manifest.each do |line|
    hash, name = line.split
    raise "hash #{name}" unless Digest::SHA256.file("#{root}/#{run}/#{name}").hexdigest == hash
  end
end
raise 'runs differ' unless File.binread("#{root}/run1/checksums.sha256") == File.binread("#{root}/run2/checksums.sha256")
scenes = %w[hotspots-4500 hotspots-45000 uniform-45000]
source = {}
Zlib::GzipReader.open(input) do |gz|
  CSV.new(gz, headers: true).each do |r|
    next unless scenes.include?(r['scene']) && %w[D G].include?(r['model'])
    key = r.values_at('scene', 'model', 'seed', 'index')
    source[key] = r.values_at('true_x', 'true_y', 'shown_x', 'shown_y').map(&:to_f)
  end
end
raise 'source count' unless source.size == 189000
ids = Set.new
pairs = {}
groups = Hash.new { |h, k| h[k] = [] }
Zlib::GzipReader.open("#{root}/run2/stars.csv.gz") do |gz|
  CSV.new(gz, headers: true).each do |r|
    key = r.values_at('scene', 'model', 'seed', 'index')
    raise 'duplicate' unless ids.add?(key)
    positions = r.values_at('true_x', 'true_y', 'x', 'y').map(&:to_f)
    raise 'source altered' unless source.fetch(key) == positions
    raise 'cohort birth' unless r['cohort_created_offset_hours'].to_f.zero?
    birth = Float(r['steady_created_offset_hours'])
    raise 'birth bounds' unless birth >= -24 && birth < 24
    identity = r.values_at('scene', 'seed', 'index')
    raise 'D/G birth differs' if pairs.key?(identity) && pairs[identity] != birth
    pairs[identity] = birth
    groups[r.values_at('scene', 'model')] << birth
  end
end
raise 'event count' unless ids.size == 189000 && pairs.size == 94500 && groups.size == 6
alpha = lambda do |age|
  next 0.0 if age < 0 || age >= 24
  next 1.0 if age <= 2
  u = (age - 2) / 22.0
  1 - 3 * u**2 + 2 * u**3
end
metrics = CSV.read("#{root}/run2/metrics.csv", headers: true)
raise 'metric rows' unless metrics.size == 120
conditions = Set.new
common = {}
metrics.each do |r|
  raise 'duplicate metric' unless conditions.add?(r.values_at('scene', 'schedule', 'hour', 'model', 'style'))
  births = groups.fetch(r.values_at('scene', 'model'))
  hour = Float(r['hour'])
  raise 'hour' unless [0, 6, 12, 18, 24].include?(hour)
  raise 'schedule' unless %w[cohort steady].include?(r['schedule'])
  raise 'style' unless %w[A B].include?(r['style'])
  ages = births.map { |b| hour - (r['schedule'] == 'cohort' ? 0 : b) }
  active = ages.select { |a| a >= 0 && a < 24 }
  opacity = active.map { |a| alpha.call(a) }
  expected = { 'total' => births.size, 'unborn' => ages.count(&:negative?), 'eligible' => active.size,
               'expired' => ages.count { |a| a >= 24 }, 'alpha_sum' => opacity.sum,
               'mean_alpha' => opacity.empty? ? 0 : opacity.sum / opacity.size,
               'alpha_ge_half' => opacity.count { |a| a >= 0.5 } }
  expected.each { |k, v| raise "metric #{k}" unless (Float(r[k]) - v).abs < 1e-6 }
  identity = r.values_at('scene', 'schedule', 'hour')
  values = r.values_at(*expected.keys)
  raise 'D/G/A/B differ' if common.key?(identity) && common[identity] != values
  common[identity] = values
end
raise 'missing condition' unless common.size == 30
pngs = Dir["#{root}/run2/*.png"]
raise 'PNG count' unless pngs.size == 37
pngs.each do |p|
  dimensions = p.end_with?('age-legend.png') ? [1600, 420] : p.end_with?('-timeline.png') ? [2000, 560] : [1600, 1860]
  raise 'PNG dimensions' unless File.binread(p)[16, 8].unpack('NN') == dimensions
end
raise 'GIF count' unless Dir["#{root}/run2/*.gif"].size == 6
puts 'PASS: both 45-file hashes; 189000 E007 coordinates preserved; 94500 D/G births shared; 120 metric rows recomputed; D/G/A/B opacity and counts equal; 37 PNG dimensions; 6 GIF files'
