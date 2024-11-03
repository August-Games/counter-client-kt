# Counter-client (kt)

> [!NOTE]
> The name 'counter' is crap, and needs to be revised at some point.

## Problem statement

As a software developer for a business, I want to have a really simple way to update and keep track of 'counters' for various different parts of the software.

e.g.
1. Registered users count over time
2. Auction House system: tax taken over time
3. Overall in-game currency sink count over time
4. Number of times users have died (in-game) in a particular raid over time
5. Number of store purchases made over time

I want a single, simple solution, which isn't full of massive amounts of other 'analytics features' that are not needed to solve this problem.

I want the software that solves this problem to be **reliable**, **performant**, and **configurable**, allowing me to use the software under extremely heavy load.

## How to use this

> [!IMPORTANT]  
> In order to use this, you need a valid API key that is provisioned by the backend counterpart to this library.
> This cannot be self hosted, and currently is not available to the public.

### Step 1: Construct a `CounterService`

`CounterService` has a simple `createDefault` function within its `companion object`.

This will create an instance of the `DefaultCounterService` which handles batching of updates queued up by the caller, and flushing them out repeatedly in user-configured batch sizes, at user-configured time periods.

Pass in an instance of `CounterConfig` as per your own requirements. This is where you pass in your API key as well.

### Step 2: Invoke `CounterService.start()`

Calling `start()` is required to start up the regular flushing of queued counter updates.

Notice there is also `pause()` and `resume()` which allows you to build up a backlog of `CounterUpdate`s for whatever reason, and flush them out at a later point when you resume the flushing.

### Step 3: Start invoking `updateCounter`/`batchUpdateCounter`

Refer to [App.kt](app/src/main/kotlin/App.kt) for example usage.

You can simply call `updateCounter` with a `CounterUpdate` where you specify the `tag` and the `added`/`removed` amount.
```kt
service.updateCounter(
    update =
        CounterUpdate(
            tag = "example-app-counter",
            added = BigNumber.create(amount = 1),
        ),
)
```

That's really all there is to it!

You can optionally call `batchUpdateCounter` with a `List<CounterUpdate>` if you'd like to queue up more than one at at time for some reason.

### Step 4: Invoke `CounterService.shutdown()`

If you're able to hook into something at, or near, the end of your application lifecycle, then you can call `shutdown()` to cancel the automatic flushing job, and optionally suspend until the final batches have been synced with the backend.
