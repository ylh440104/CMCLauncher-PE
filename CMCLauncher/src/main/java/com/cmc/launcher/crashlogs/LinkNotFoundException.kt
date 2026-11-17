package com.cmc.launcher.crashlogs

class LinkNotFoundException : RuntimeException(
    "Unable to find an available link from the data returned by the remote end"
)
