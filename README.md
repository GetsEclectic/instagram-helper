# instagram4k

[![MIT License](http://img.shields.io/badge/license-MIT-green.svg)](https://github.com/getseclectic/instagram4k/blob/master/LICENSE) [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](http://makeapullrequest.com) [![Maintainability](https://api.codeclimate.com/v1/badges/7865899a39e952e825d4/maintainability)](https://codeclimate.com/github/GetsEclectic/instagram4k/maintainability) [![Test Coverage](https://api.codeclimate.com/v1/badges/7865899a39e952e825d4/test_coverage)](https://codeclimate.com/github/GetsEclectic/instagram4k/test_coverage)

kotlin instagram library using [instagram4j](https://github.com/brunocvcunha/instagram4j)

## Examples

#### login

```kotlin
val instagram4K = Instagram4K("username", "password")
```

#### see how many people you're following that aren't following you back

```kotlin
println(instagram4K.getUnfollowerPKs().size)
```

#### unfollow those ingrates

```kotlin
instagram4K.unfollowUnfollowers()
```

#### unfollow people that are following you but probably won't unfollow you if you unfollow them

```kotlin
instagram4K.pruneMutualFollowers()
```

#### follow some people that are following a similar account and will probably follow you back

```kotlin
instagram4K.copyFollowers("name_of_a_similar_account")
```

## Terms and conditions

- You will NOT use this API for marketing purposes (spam, botting, harassment, massive bulk messaging...).
- We do NOT give support to anyone who wants to use this API to send spam or commit other crimes.
- We reserve the right to block any user of this repository that does not meet these conditions.

### Legal

This code is in no way affiliated with, authorized, maintained, sponsored or endorsed by Instagram or any of its affiliates or subsidiaries. This is an independent and unofficial API. Use at your own risk.

### License

This code is released under the [MIT license](https://opensource.org/licenses/MIT).
