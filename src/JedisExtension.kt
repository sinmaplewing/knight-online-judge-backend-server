package com.maplewing

import redis.clients.jedis.Jedis

fun Jedis?.getConnection(): Jedis =
    if (this == null || !this.isConnected) Jedis() else this

