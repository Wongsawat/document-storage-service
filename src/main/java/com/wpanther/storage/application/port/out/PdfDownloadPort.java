package com.wpanther.storage.application.port.out;

/**
 * Outbound port for downloading PDF files from external URLs.
 * Implemented by infrastructure/adapter/out/http/PdfDownloadAdapter.
 */
public interface PdfDownloadPort {

    /**
     * Downloads a PDF from the given URL and returns its bytes.
     *
     * @param url the URL to download from (e.g. MinIO presigned URL)
     * @return the downloaded PDF bytes
     */
    byte[] downloadPdf(String url);
}
