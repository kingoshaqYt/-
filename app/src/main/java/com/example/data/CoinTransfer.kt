package com.example.data

data class CoinTransfer(
    val id: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val toUid: String = "",
    val toName: String = "",
    val amount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = ""
)
