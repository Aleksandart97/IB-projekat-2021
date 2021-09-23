package app;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.mail.internet.MimeMessage;

import org.apache.xml.security.utils.JavaUtils;

import com.google.api.services.gmail.Gmail;

import util.Base64;
import util.GzipUtil;
import util.IVHelper;
import support.MailHelper;
import support.MailWritter;
import keystore.KeyStoreReader;
import model.mailclient.MailBody;

public class WriteMailClient extends MailClient {

	private static final String KEY_FILE = "./data/session.key";
	private static final String IV1_FILE = "./data/iv1.bin";
	private static final String IV2_FILE = "./data/iv2.bin";
	private static final String KEY_STORE_USER_A = "./data/userA.jks";
	private static final String KEY_STORE_USER_B = "./data/userB.jks";
	private static final String KEY_STORE_USER_A_PASS = "123";
	private static final String KEY_STORE_USER_B_ALIAS = "userb";
	private static final String KEY_STORE_USER_A_ALIAS = "usera";
	private static final String KEY_STORE_USER_B_PASS = "123";
	
	
	public static void main(String[] args) {
		
        try {
        	Gmail service = getGmailService();
            
        	System.out.println("Insert a reciever:");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String reciever = reader.readLine();
        	
            System.out.println("Insert a subject:");
            String subject = reader.readLine();
            
            
            System.out.println("Insert body:");
            String body = reader.readLine();
            
            KeyStoreReader keyStoreReader = new KeyStoreReader();
            KeyStore keyStoreUserA = keyStoreReader.readKeyStore(KEY_STORE_USER_A, KEY_STORE_USER_A_PASS.toCharArray());
            Certificate certificateUserB = keyStoreReader.getCertificateFromKeyStore(keyStoreUserA, KEY_STORE_USER_B_ALIAS);
            PublicKey publicKeyUserB = keyStoreReader.getPublicKeyFromCertificate(certificateUserB);
            PrivateKey privateKeyUserA = keyStoreReader.getPrivateKeyFromKeyStore(keyStoreUserA, KEY_STORE_USER_A_ALIAS, KEY_STORE_USER_A_PASS.toCharArray());
            System.out.println("user B certificate: " + certificateUserB);
            System.out.println("-----------------------------------------------------------------------");
            System.out.println("Public key user B: " + publicKeyUserB);
            System.out.println("-----------------------------------------------------------------------");
            System.out.println("Private key user B: " + privateKeyUserA);
            System.out.println("-----------------------------------------------------------------------");
            
            //Compression
            String compressedSubject = Base64.encodeToString(GzipUtil.compress(subject));
            String compressedBody = Base64.encodeToString(GzipUtil.compress(body));
            
            //Key generation
            KeyGenerator keyGen = KeyGenerator.getInstance("AES"); 
			SecretKey secretKey = keyGen.generateKey();
			Cipher aesCipherEnc = Cipher.getInstance("AES/CBC/PKCS5Padding");
			
			//inicijalizacija za sifrovanje 
			IvParameterSpec ivParameterSpec1 = IVHelper.createIV();
			//inicijalizacija ciphera
			aesCipherEnc.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec1);
			
			
			//sifrovanje
			byte[] ciphertext = aesCipherEnc.doFinal(compressedBody.getBytes());
			
			
			String ciphertextStr = Base64.encodeToString(ciphertext);
			System.out.println("Kriptovan tekst: " + ciphertextStr);
			System.out.println("-----------------------------------------------------------------------");
			
			
			//inicijalizacija za sifrovanje 
			IvParameterSpec ivParameterSpec2 = IVHelper.createIV();
			aesCipherEnc.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec2);
			
			
			//sifrovanje subjecta
			byte[] ciphersubject = aesCipherEnc.doFinal(compressedSubject.getBytes());
			
			
			String ciphersubjectStr = Base64.encodeToString(ciphersubject);
			System.out.println("Kriptovan subject: " + ciphersubjectStr);
			System.out.println("-----------------------------------------------------------------------");
			
			//Sifrovanje tajnog/session kljuca javnim kljucem
			Cipher rsaCipherEnc = Cipher.getInstance("RSA/ECB/PKCS1Padding");
			rsaCipherEnc.init(Cipher.ENCRYPT_MODE, publicKeyUserB);
			byte[] cipherKey = rsaCipherEnc.doFinal(secretKey.getEncoded());
			
			
			//snimaju se bajtovi kljuca i IV.
			JavaUtils.writeBytesToFilename(KEY_FILE, secretKey.getEncoded());
			JavaUtils.writeBytesToFilename(IV1_FILE, ivParameterSpec1.getIV());
			JavaUtils.writeBytesToFilename(IV2_FILE, ivParameterSpec2.getIV());
			
			byte [] ivP1 = ivParameterSpec1.getIV();
			byte [] ivP2 = ivParameterSpec2.getIV();
			
			//Potpisivanje
			
			//Creating a Signature object sa odredjenim algoritmom
			Signature signature = Signature.getInstance("SHA1withRSA");
			
			//Initialize the signature
		    signature.initSign(privateKeyUserA);
		  
		    //byte[] bytes = ciphertext;
		    
		    //Adding data to the signature // add the received message bytes to the signature object by invoking the update method
		    signature.update(ciphertext);
		    
		    //Calculating the signature
		    byte[] sgn = signature.sign();
		    System.out.println("Signature: " + sgn);
		    System.out.println("-----------------------------------------------------------------------");
			
			MailBody mailBody = new MailBody(ciphertext, ivP1, ivP2,  cipherKey, sgn);
			
			
			
        	MimeMessage mimeMessage = MailHelper.createMimeMessage(reciever, ciphersubjectStr, mailBody.toCSV());
        	MailWritter.sendMessage(service, "me", mimeMessage);
        	
        }catch (Exception e) {
        	e.printStackTrace();
		}
	}
}
