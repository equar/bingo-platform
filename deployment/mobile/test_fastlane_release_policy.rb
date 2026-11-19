module UI
  class UserError < StandardError
  end

  def self.user_error!(message)
    raise UserError, message
  end
end

def default_platform(*)
end

def desc(*)
end

def platform(*)
  yield
end

def lane(*)
end

load File.expand_path('../../bingo-player/fastlane/Fastfile', __dir__)

def expect_rejected(label)
  yield
rescue UI::UserError
  return
else
  raise "#{label} was accepted."
end

validate_release_base_url!('http://api.test.bakaroo.com', allow_test_http: true)
validate_production_base_url!('https://api.bingo.bakaroo.com')

expect_rejected('test HTTP without explicit opt-in') do
  validate_release_base_url!('http://api.test.bakaroo.com')
end
expect_rejected('legacy test hostname') do
  validate_release_base_url!(
    'http://api.test.bingo.bakaroo.com',
    allow_test_http: true
  )
end
expect_rejected('production build using test API') do
  validate_production_base_url!('https://api.test.bakaroo.com')
end
expect_rejected('production build using HTTP') do
  validate_production_base_url!('http://api.bingo.bakaroo.com')
end

puts 'Fastlane release URL policy checks passed.'
