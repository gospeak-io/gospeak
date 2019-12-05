# Taken decisions

As the project has already almost one year of existence, many decisions are already taken so let's review some of them!

## technical stack

The current technical stack was chosen to be friendly and productive, so I took the tools I like and know already.

Here is a list:
- **Scala**: because I <3 it and it allows well typed FP code
- **Play framework**: because it's mainstream, I had good experience with and and offer nice features (action composition, routes, server side rendering...)
- **Twirl**: because it seems much quicker to build basic pages using it than SPA (React or Angular), full power of Scala & HTML, lot of re-usability and flexibility thanks to templates as functions
- **Silhouette**: because it seems to be the more serious auth lib for Play, but miss a lot of doc :(
- **Hocon**/**Pureconfig**: standard in Play app (Hocon). Pureconfig allows to fail fast (no app startup if bad config)
- **Compile time DI**: no magic, doing some `new` in ApplicationLoader is very easy
- **PostgreSQL**/**H2**: SQL as mainstream and general purpose storage, PostgreSQL as a powerful db and easily available everywhere, H2 for convenient dev cycle
- **Doobie**: to stay near to raw SQL, very convenient to map result to case classes, provide an excellent query checker (`check`)
- **Flyway**: to handle nicely SQL migrations, liquibase seems too old and less convenient
- **Flexmark**: to handle user templating, it was the easier to integrate but an other template engine, more powerful (logicful) may be more adapted
- **Cats**: to use `IO` and `NonEmptyList` essentially, `OptionT` were also quite useful
- **TypeScript**: I can't live without types ^^
- **SCSS**: better CSS: @import & $variable
- **DDD**: to keep code as understandable as possible, I'm a big fan!
- **Hexagonal architecture**: to experiment, not really well implemented yet but I keep faith ^^
- **Heroku**: easy to use (`git push` ^^), free for dev
- **SendGrid**: heroku addon, already used before

## Db `env` table

If you look at application startup (`GospeakApplicationLoader`), you can find a `db.checkEnv` in the `onStart` method which may seems strange.

This function create an `env` table in the database and store the application `env` if it does not exists.

It allows to guarantee that application and database have the same environment and prevent mixing them.

As Gospeak has some behaviours which are environment dependant (such as dropping db ^^), you really dont want to fail on this.

## String lists stored as a joined text fields

This is a quick and dirty hack that should be removed when we have time.

Many id lists are saved joining them in a text field, this is practical to get them all at once, especially in list selections (having orga ids when listing groups).

This is far from ideal because of missing foreign keys checks which prevent from integrity and joins.

## SQL query builder helpers

Doobie is an awesome tool, but writing SQL queries can quickly become repetitive, and mostly when you want to select columns.

DoobieUtils is here to help with this (mainly the `Table` class), defining SQL tables and building correct SQL queries.
