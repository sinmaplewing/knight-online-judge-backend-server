package com.maplewing

import io.ktor.auth.Principal

data class UserIdAuthorityPrincipal(val userId: String, val authority: String) : Principal