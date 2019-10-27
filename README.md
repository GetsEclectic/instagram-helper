# instagram4k

[![MIT License](http://img.shields.io/badge/license-MIT-green.svg)](https://github.com/getseclectic/instagram4k/blob/master/LICENSE) [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](http://makeapullrequest.com) [![Maintainability](https://api.codeclimate.com/v1/badges/7865899a39e952e825d4/maintainability)](https://codeclimate.com/github/GetsEclectic/instagram4k/maintainability) [![Test Coverage](https://api.codeclimate.com/v1/badges/7865899a39e952e825d4/test_coverage)](https://codeclimate.com/github/GetsEclectic/instagram4k/test_coverage)

kotlin instagram library using [instagram4j](https://github.com/brunocvcunha/instagram4j)

## Example usage

```kotlin
Instagram4K("instagram username", "instagram password").use { instagram4k ->
        // unfollow users that aren't following you back
        instagram4k.unfollowUnfollowers(100)
        
        // follow users likely to like your posts
        // it will follow users that have liked the top posts for the tags it is exploring
        // it uses thompson sampling to explore/exploit the tags you've used in recent posts on your account
        instagram4k.applyThompsonSamplingToExploreTagsToFollowFrom(100)

        // similar to the previous function
        // it likes the three most recent posts for accounts that have liked the top posts for the tags it is exploring
        instagram4k.applyThompsonSamplingToExploreTagsToLikeFrom(100)

        // unfollows users that are following you and follow many more people than are following them
        // should be likely to continue following you
        instagram4k.pruneMutualFollowers(100)

        // record to the database all the accounts that have liked your recent posts
        // this data is necessary for thompson sampling
        instagram4k.recordLikers()

        // record to the database all of the accounts that have followed or unfollowed you since the last time it was run
        // this data is not used by this application yet, but it can be interesting to look at
        instagram4k.recordFollowers()
}
```

## Set up
```bash
./setUpDb.sh
```

This will:
- start a postgres 11 docker container
- create an instagram4k database and instagram4k_app user
- create a local config.properties file containing the user password, this is used by the app, flyway, and jooq
- run the flywayMigrate and generateInstagram4KJooqSchemaSource gradle tasks

You will need to set up tasker, autoremote, and autoinput separately, and put your autoremote url into config.properties.

### Legal

This code is in no way affiliated with, authorized, maintained, sponsored or endorsed by Instagram or any of its affiliates or subsidiaries. This is an independent and unofficial API. Use at your own risk.

### License

This code is released under the [MIT license](https://opensource.org/licenses/MIT).
