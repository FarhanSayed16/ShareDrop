package com.classdrop.model;

import com.classdrop.exception.TransferFailedException;
import java.io.InputStream;

public interface Transferable {
    String getId();
    long getSizeBytes();
    String getContentType();
    InputStream openStream() throws TransferFailedException;
}
