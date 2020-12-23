package com.io2c.httpproxyserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.URL;
import java.security.*;
import java.security.cert.CertificateException;

public class SslContextCreator {

    private static Logger logger = LoggerFactory.getLogger(SslContextCreator.class);

    public SSLContext initSSLContext(String jksPath, String keyStorePassword, String keyManagerPassword) {
        logger.info("Initializing SSL context. KeystorePath = {}.", jksPath);
        if (jksPath == null || jksPath.isEmpty()) {
            // key_store_password or key_manager_password are empty
            logger.warn("The keystore path is null or empty. The SSL context won't be initialized.");
            return null;
        }

        if (keyStorePassword == null || keyStorePassword.isEmpty()) {
            // key_store_password or key_manager_password are empty
            logger.warn("The keystore password is null or empty. The SSL context won't be initialized.");
            return null;
        }

        if (keyManagerPassword == null || keyManagerPassword.isEmpty()) {
            // key_manager_password or key_manager_password are empty
            logger.warn("The key manager password is null or empty. The SSL context won't be initialized.");
            return null;
        }
        try {
            logger.info("Loading keystore. KeystorePath = {}.", jksPath);
            InputStream jksInputStream = jksDataStore(jksPath);
            SSLContext serverContext = SSLContext.getInstance("SSLv3");
            final KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(jksInputStream, keyStorePassword.toCharArray());
            logger.info("Initializing key manager...");
            final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, keyManagerPassword.toCharArray());
            // init sslContext
            logger.info("Initializing SSL context...");
            serverContext.init(kmf.getKeyManagers(), null, null);
            logger.info("The SSL context has been initialized successfully.");

            return serverContext;
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException | CertificateException | KeyStoreException
                | KeyManagementException | IOException ex) {
            logger.error("Unable to initialize SSL context. Cause = {}, errorMessage = {}.", ex.getCause(),
                    ex.getMessage());
            return null;
        }
    }

    private InputStream jksDataStore(String jksPath) throws FileNotFoundException {
        URL jksUrl = getClass().getClassLoader().getResource(jksPath);
        if (jksUrl != null) {
            logger.info("Starting with jks at {}, jks normal {}", jksUrl.toExternalForm(), jksUrl);
            return getClass().getClassLoader().getResourceAsStream(jksPath);
        }

        logger.warn("No keystore has been found in the bundled resources. Scanning filesystem...");
        File jksFile = new File(jksPath);
        if (jksFile.exists()) {
            logger.info("Loading external keystore. Url = {}.", jksFile.getAbsolutePath());
            return new FileInputStream(jksFile);
        }

        logger.warn("The keystore file does not exist. Url = {}.", jksFile.getAbsolutePath());
        return null;
    }
}