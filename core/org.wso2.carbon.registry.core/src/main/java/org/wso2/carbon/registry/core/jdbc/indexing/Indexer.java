/*
 * Copyright (c) 2008, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.registry.core.jdbc.indexing;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.jdbc.JdbcDirectory;
import org.apache.poi.hslf.extractor.PowerPointExtractor;
import org.apache.poi.hssf.extractor.ExcelExtractor;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.pdfbox.cos.COSDocument;
import org.pdfbox.pdfparser.PDFParser;
import org.pdfbox.pdmodel.PDDocument;
import org.pdfbox.util.PDFTextStripper;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.config.RegistryContext;
import org.wso2.carbon.registry.core.dao.ResourceDAO;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.jdbc.handlers.RequestContext;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

public class Indexer {

    private static final String MSG_FAILED_WRITE_TO_INDEX = "Failed to write to the index";

	private static final String FIELD_NAME_CONTENT = "content";

	private static final Log log = LogFactory.getLog(Indexer.class);

    private StringBuffer sb = new StringBuffer();
    private String id;
    private String contentString;
    private String url;
    private InputStream is;
    private BufferedReader br;

    private URL resourceURL;

    public void updateIndex(RequestContext requestContext) throws RegistryException {
        String line;
        Resource resource = requestContext.getResource();

        try {
            getId(requestContext);
            Object contentObj = resource.getContent();
            byte[] content;
            if (contentObj instanceof String) {
                content = ((String) contentObj).getBytes();
            } else {
                content = (byte[]) contentObj;
            }
            is = resource.getContentStream();
            if (content != null) {
                contentString = new String(content);
            }
            url = requestContext.getSourceURL();
            if (url != null) {
                validateForLocalUrl(url);
                resourceURL = new URL(url);
                is = resourceURL.openStream();

                br = new BufferedReader(new InputStreamReader(is));
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                contentString = sb.toString();
                is.close();
            }

            Document document = new Document();
            document.add(new Field("id", id, Field.Store.YES, Field.Index.TOKENIZED));
            document.add(
                    new Field(FIELD_NAME_CONTENT, contentString, Field.Store.NO, Field.Index.TOKENIZED));
            IndexWriter writer = new IndexWriter(RegistryContext.getBaseInstance().getJdbcDir(),
                    new StandardAnalyzer());
            writer.addDocument(document);
            writer.optimize();
            writer.close();
        } catch (CorruptIndexException e) {
        } catch (IOException e) {
        }
    }

    public void indexXML(RequestContext requestContext) throws RegistryException {
        String line;
        final StringBuffer contentOnly = new StringBuffer();
        Resource resource = requestContext.getResource();

        try {
            getId(requestContext);
            is = resource.getContentStream();
            url = requestContext.getSourceURL();
            if (url != null) {
                validateForLocalUrl(url);
                resourceURL = new URL(url);
                is = resourceURL.openStream();
            }

            DefaultHandler handler = new DefaultHandler() {
                public void characters(char ch[], int start, int length) throws SAXException {
                    contentOnly.append(new String(ch, start, length));
                }
            };
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(is, handler);

            if (url != null) {
                is = resourceURL.openStream();
            } else {
                is = resource.getContentStream();
            }
            br = new BufferedReader(new InputStreamReader(is));
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            is.close();

            Document document = new Document();
            document.add(new Field("id", id, Field.Store.YES, Field.Index.TOKENIZED));
            document.add(
                    new Field(FIELD_NAME_CONTENT, sb.toString(), Field.Store.NO, Field.Index.TOKENIZED));
            document.add(new Field("contentOnly", contentOnly.toString(), Field.Store.NO,
                    Field.Index.TOKENIZED));

            IndexWriter writer = new IndexWriter(RegistryContext.getBaseInstance().getJdbcDir(),
                    new StandardAnalyzer());
            writer.addDocument(document);
            writer.optimize();
            writer.close();
        } catch (IOException e) {
            String msg = MSG_FAILED_WRITE_TO_INDEX;
            log.error(msg);
            throw new RegistryException(msg, e);
        } catch (SAXException e) {
            String msg = "Failed to parse XML";
            log.error(msg);
            throw new RegistryException(msg, e);
        } catch (ParserConfigurationException e) {
            String msg = "Failed to parse XML";
            log.error(msg);
            throw new RegistryException(msg, e);
        }

    }

    public void indexPDF(RequestContext requestContext) throws RegistryException {
        Resource resource = requestContext.getResource();

        try {
            getId(requestContext);
            is = resource.getContentStream();
            url = requestContext.getSourceURL();
            if (url != null) {
                validateForLocalUrl(url);
                resourceURL = new URL(url);
                is = resourceURL.openStream();
            }

            PDFParser parser = new PDFParser(is);
            parser.parse();
            COSDocument cosDoc = parser.getDocument();

            PDFTextStripper stripper = new PDFTextStripper();
            String docText = stripper.getText(new PDDocument(cosDoc));
            cosDoc.close();
            Document document = new Document();
            document.add(new Field("id", id, Field.Store.YES, Field.Index.TOKENIZED));
            document.add(
                    new Field(FIELD_NAME_CONTENT, docText, Field.Store.NO, Field.Index.TOKENIZED));
            IndexWriter writer = new IndexWriter(RegistryContext.getBaseInstance().getJdbcDir(),
                    new StandardAnalyzer());
            writer.addDocument(document);
            writer.optimize();
            writer.close();
        } catch (IOException e) {
            String msg = MSG_FAILED_WRITE_TO_INDEX;
            log.error(msg);
            throw new RegistryException(msg, e);
        }
    }

    public void indexMSWord(RequestContext requestContext) throws RegistryException {
        Resource resource = requestContext.getResource();
        try {
            getId(requestContext);
            is = resource.getContentStream();
            url = requestContext.getSourceURL();
            if (url != null) {
                validateForLocalUrl(url);
                resourceURL = new URL(url);
                is = resourceURL.openStream();
            }

            POIFSFileSystem fs = new POIFSFileSystem(is);
            WordExtractor extractor = new WordExtractor(fs);
            String wordText = extractor.getText();

            Document document = new Document();
            document.add(new Field("id", id, Field.Store.YES, Field.Index.TOKENIZED));
            document.add(
                    new Field(FIELD_NAME_CONTENT, wordText, Field.Store.NO, Field.Index.TOKENIZED));
            IndexWriter writer = new IndexWriter(RegistryContext.getBaseInstance().getJdbcDir(),
                    new StandardAnalyzer());
            writer.addDocument(document);
            writer.optimize();
            writer.close();
        } catch (IOException e) {
            String msg = MSG_FAILED_WRITE_TO_INDEX;
            log.error(msg);
            throw new RegistryException(msg, e);
        }
    }

    public void indexMSExcel(RequestContext requestContext) throws RegistryException {
        Resource resource = requestContext.getResource();
        try {
            getId(requestContext);
            is = resource.getContentStream();
            url = requestContext.getSourceURL();
            if (url != null) {
                validateForLocalUrl(url);
                resourceURL = new URL(url);
                is = resourceURL.openStream();
            }

            POIFSFileSystem fs = new POIFSFileSystem(is);
            ExcelExtractor extractor = new ExcelExtractor(fs);
            String excelText = extractor.getText();

            Document document = new Document();
            document.add(new Field("id", id, Field.Store.YES, Field.Index.TOKENIZED));
            document.add(
                    new Field(FIELD_NAME_CONTENT, excelText, Field.Store.NO, Field.Index.TOKENIZED));
            IndexWriter writer = new IndexWriter(RegistryContext.getBaseInstance().getJdbcDir(),
                    new StandardAnalyzer());
            writer.addDocument(document);
            writer.optimize();
            writer.close();
        } catch (IOException e) {
            String msg = MSG_FAILED_WRITE_TO_INDEX;
            log.error(msg);
            throw new RegistryException(msg, e);
        }
    }

    public void indexMSPowerpoint(RequestContext requestContext) throws RegistryException {
        Resource resource = requestContext.getResource();
        try {
            getId(requestContext);
            is = resource.getContentStream();
            url = requestContext.getSourceURL();
            if (url != null) {
                validateForLocalUrl(url);
                resourceURL = new URL(url);
                is = resourceURL.openStream();
            }

            POIFSFileSystem fs = new POIFSFileSystem(is);
            PowerPointExtractor extractor = new PowerPointExtractor(fs);
            String ppText = extractor.getText();

            Document document = new Document();
            document.add(new Field("id", id, Field.Store.YES, Field.Index.TOKENIZED));
            document.add(
                    new Field(FIELD_NAME_CONTENT, ppText, Field.Store.NO, Field.Index.TOKENIZED));
            IndexWriter writer = new IndexWriter(RegistryContext.getBaseInstance().getJdbcDir(),
                    new StandardAnalyzer());
            writer.addDocument(document);
            writer.optimize();
            writer.close();
        } catch (IOException e) {
            String msg = MSG_FAILED_WRITE_TO_INDEX;
            log.error(msg);
            throw new RegistryException(msg, e);
        }
    }

    public void deleteFromIndex(RequestContext requestContext) throws RegistryException {
        JdbcDirectory jdbcDir = RegistryContext.getBaseInstance().getJdbcDir();
        Resource resource = requestContext.getResource();
        id = resource.getId();

        try {
            IndexReader reader = IndexReader.open(jdbcDir);
            Term term = new Term("id", id);
            if (reader.docFreq(term) > 0) {
                reader.deleteDocuments(term);
            }
            reader.close();
        } catch (IOException e) {
            String msg = "Failed to delete from the index";
            log.error(msg);
            throw new RegistryException(msg, e);
        }
    }

    private void validateForLocalUrl(String url) throws RegistryException {
        if (url != null && url.toLowerCase().startsWith("file:")) {
            String msg = "The source URL must not be file in the server's local file system";
            throw new RegistryException(msg);
        }
    }

    private void getId(RequestContext requestContext) throws RegistryException {
        throw new UnsupportedOperationException();
//TODO:*
//        Resource resource = requestContext.getResource();
//        String path = requestContext.getResourcePath().getPath();
//        JdbcDirectory jdbcDir = RegistryContext.getBaseInstance().getJdbcDir();
//
//        try {
//            if (resourceDAO.resourceExists(path)) {
//                id = resourceDAO.getResourceID(path, RegistryContext.getBaseInstance().getDataSource().
//                        getConnection());
//                if (IndexReader.indexExists(jdbcDir)) {
//                    IndexReader reader = IndexReader.open(jdbcDir);
//                    Term term = new Term("id", id);
//                    if (reader.docFreq(term) > 0) {
//                        reader.deleteDocuments(term);
//                    }
//                    reader.close();
//                }
//            } else {
//                id = resource.getId();
//            }
//        } catch (IOException e) {
//            String msg = "Failed to write to the index";
//            log.error(msg);
//            throw new RegistryException(msg);
//        } catch (SQLException e) {
//            String msg = "Failed to connect with the database";
//            log.error(msg);
//            throw new RegistryException(msg);
//        }
    }
}
