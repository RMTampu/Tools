package com.toolbox.tools.integration;
public final class ExportPackage {
 private final String payload; private final String sha256;
 public ExportPackage(String payload,String sha256){this.payload=payload;this.sha256=sha256;}
 public String payload(){return payload;} public String sha256(){return sha256;}
}
