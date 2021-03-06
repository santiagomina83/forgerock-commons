/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2013-2015 ForgeRock AS.
 */

package org.forgerock.json.jose.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * A class that manages a Java Key Store and has methods for extracting out public/private keys and certificates.
 *
 * @since 2.0.0
 */
public class KeystoreManager {

    private KeyStore keyStore = null;

    /**
     * Constructs an instance of the KeystoreManager.
     *
     * @param keyStoreType The type of Java KeyStore.
     * @param keyStoreFile The file path to the KeyStore.
     * @param keyStorePassword The password for the KeyStore.
     */
    public KeystoreManager(String keyStoreType, String keyStoreFile,
            String keyStorePassword) {
        loadKeyStore(keyStoreType, keyStoreFile, keyStorePassword);
    }

    /**
     * Loads the KeyStore based on the given parameters.
     *
     * @param keyStoreType The type of Java KeyStore.
     * @param keyStoreFile The file path to the KeyStore.
     * @param keyStorePassword The password for the KeyStore.
     */
    private void loadKeyStore(String keyStoreType, String keyStoreFile, String keyStorePassword) {
        try {
            keyStore = KeyStore.getInstance(keyStoreType);
            if (keyStoreFile == null || keyStoreFile.isEmpty()) {
                throw new KeystoreManagerException("mapPk2Cert.JKSKeyProvider: KeyStore FileName is null, "
                        + "unable to establish Mapping Public Keys to Certificates!");
            }
            FileInputStream fis = new FileInputStream(keyStoreFile);
            keyStore.load(fis, keyStorePassword.toCharArray());
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            throw new KeystoreManagerException(e);
        }
    }

    /**
     * Gets the certificate from the KeyStore with the given alias.
     *
     * @param certAlias The Certificate Alias.
     * @return The Certificate.
     */
    public Certificate getCertificate(String certAlias)  {
        if (certAlias == null || certAlias.length() == 0) {
            return null;
        }

        try {
            return keyStore.getCertificate(certAlias);
        } catch (KeyStoreException e) {
            throw new KeystoreManagerException(e);
        }
    }

    /**
     * Gets a X509Certificate from the KeyStore with the given alias.
     *
     * @param certAlias The Certificate Alias.
     * @return The X509Certificate.
     */
    public X509Certificate getX509Certificate(String certAlias) {
        Certificate certificate = getCertificate(certAlias);
        if (certificate instanceof X509Certificate) {
            return (X509Certificate) certificate;
        }
        throw new KeystoreManagerException("Certificate not a X509 Certificate for alias: " + certAlias);
    }

    /**
     * Gets the Public Key from the KeyStore with the given alias.
     *
     * @param keyAlias The Public Key Alias.
     * @return The Public Key.
     */
    public PublicKey getPublicKey(String keyAlias) {
        if (keyAlias == null || keyAlias.isEmpty()) {
            return null;
        }

        X509Certificate cert = getX509Certificate(keyAlias);
        if (cert == null) {
            throw new KeystoreManagerException("Unable to retrieve certificate for alias: " + keyAlias);
        }
        return cert.getPublicKey();
    }

    /**
     * Gets the Private Key from the KeyStore with the given alias.
     *
     * @param keyAlias The Private Key Alias.
     * @param privateKeyPassword The private key password
     * @return The Private Key.
     */
    public PrivateKey getPrivateKey(String keyAlias, String privateKeyPassword) {

        if (keyAlias == null || keyAlias.length() == 0) {
            return null;
        }

        try {
            return (PrivateKey) keyStore.getKey(keyAlias, privateKeyPassword.toCharArray());
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new KeystoreManagerException(e);
        }
    }
}
