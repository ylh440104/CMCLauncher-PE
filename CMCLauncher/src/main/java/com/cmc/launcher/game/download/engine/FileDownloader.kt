package com.cmc.launcher.game.download.engine

class FileDownloader(
    private val request: DownloadRequest,
    private val stats: DownloadStats
) {
    suspend fun download() {
        Fetcher.downloadFile(
            urls = request.urls,
            targetFile = request.targetFile,
            sha1 = request.sha1,
            onBytes = stats::addBytes
        )

        if (request.expectedSize <= 0) {
            stats.registerBytes(request.targetFile.length())
        }
    }
}
