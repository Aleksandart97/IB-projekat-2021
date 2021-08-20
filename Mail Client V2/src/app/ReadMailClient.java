package app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.xml.security.utils.JavaUtils;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;

import keystore.KeyStoreReader;
import model.mailclient.MailBody;
import support.MailHelper;
import support.MailReader;
import util.Base64;
import util.GzipUtil;

public class ReadMailClient extends MailClient {

	public static long PAGE_SIZE = 3;
	public static boolean ONLY_FIRST_PAGE = true;
	
	private static final String KEY_FILE = "./data/session.key";
	private static final String IV1_FILE = "./data/iv1.bin";
	private static final String IV2_FILE = "./data/iv2.bin";
	private static final String KEY_STORE_USER_B = "./data/userB.jks";
	private static final String KEY_STORE_USER_B_PASS = "123";
	private static final String KEY_STORE_USER_B_ALIAS = "userb";

	
	public static void main(String[] args) throws IOException, InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException, IllegalBlockSizeException, BadPaddingException, MessagingException, NoSuchPaddingException, InvalidAlgorithmParameterException {
        // Build a new authorized API client service.
        Gmail service = getGmailService();
        ArrayList<MimeMessage> mimeMessages = new ArrayList<MimeMessage>();
        
        String user = "me";
        String query = "is:unread label:INBOX";
        
        List<Message> messages = MailReader.listMessagesMatchingQuery(service, user, query, PAGE_SIZE, ONLY_FIRST_PAGE);
        for(int i=0; i<messages.size(); i++) {
        	Message fullM = MailReader.getMessage(service, user, messages.get(i).getId());
        	
        	MimeMessage mimeMessage;
			try {
				
				mimeMessage = MailReader.getMimeMessage(service, user, fullM.getId());
				
				System.out.println("\n Message number " + i);
				System.out.println("From: " + mimeMessage.getHeader("From", null));
				System.out.println("Subject: " + mimeMessage.getSubject());
				System.out.println("Body: " + MailHelper.getText(mimeMessage));
				System.out.println("\n");
				
				mimeMessages.add(mimeMessage);
	        
			} catch (MessagingException e) {
				e.printStackTrace();
			}	
        }
        
        System.out.println("Select a message to decrypt:");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
	        
	    String answerStr = reader.readLine();
	    Integer answer = Integer.parseInt(answerStr);
	    
		MimeMessage chosenMessage = mimeMessages.get(answer);
		
		//
		
		//mail body izvucen
		MailBody mailBody = new MailBody(MailHelper.getText(chosenMessage));
		
		//encripted message
		String encriptedMessage = mailBody.getEncMessage();
		byte [] entriptedKey = mailBody.getEncKeyBytes();
		byte [] signature = mailBody.getSignatureBytes();
		
		//citanje
		KeyStoreReader keyStoreReader = new KeyStoreReader();
		KeyStore keyStoreUserB = keyStoreReader.readKeyStore(KEY_STORE_USER_B, KEY_STORE_USER_B_PASS.toCharArray());
		
		//preuzimanje certifikata
		Certificate certificateUserB = keyStoreReader.getCertificateFromKeyStore(keyStoreUserB, KEY_STORE_USER_B_ALIAS);
				
		//preuzimanje privatnog kljuca
		PrivateKey privateKeyUserB = keyStoreReader.getPrivateKeyFromKeyStore(keyStoreUserB, KEY_STORE_USER_B_ALIAS, KEY_STORE_USER_B_PASS.toCharArray());
		
		//public key
		PublicKey publicKeyUserB = keyStoreReader.getPublicKeyFromCertificate(certificateUserB);
		
		//vektori
		IvParameterSpec ivP1 = new IvParameterSpec(mailBody.getIV1Bytes());
		IvParameterSpec ivP2 = new IvParameterSpec(mailBody.getIV2Bytes());
		
		//dekripcija tajnog kljuca privatnim kljucem usera B
		Cipher rsaCipherDec = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		rsaCipherDec.init(Cipher.DECRYPT_MODE, privateKeyUserB);
		byte[] secretKeyDec = rsaCipherDec.doFinal(entriptedKey);
		
		SecretKey secretKey = new SecretKeySpec(secretKeyDec, "AES");
		
		//string u byte
		byte[] encriptedMessageDec = Base64.decode(encriptedMessage);
		
		Cipher bodyCipherDec = Cipher.getInstance("AES/CBC/PKCS5Padding");
		bodyCipherDec.init(Cipher.DECRYPT_MODE, secretKey, ivP1);
		String receivedBodyTxt = new String(bodyCipherDec.doFinal(encriptedMessageDec));
		
		//dekompresija tela poruke
		String decompressedBodyText = GzipUtil.decompress(Base64.decode(new String(receivedBodyTxt)));
		System.out.println("Body: " + decompressedBodyText);
		
		//dektipcija subjecta
		bodyCipherDec.init(Cipher.DECRYPT_MODE, secretKey, ivP2);
		String decryptedSubjectTxt = new String(bodyCipherDec.doFinal(Base64.decode(chosenMessage.getSubject())));
		
		//dekompresija subjecta
		String decompressedSubjectTxt = GzipUtil.decompress(Base64.decode(decryptedSubjectTxt));
		System.out.println("Subjectt: " + decompressedSubjectTxt);
		
		//validate(publicKeyUserB, encriptedMessageDec, signature);
		
//		public static boolean validate(PublicKey publicKey,
//				 byte[] data, byte[] sign) throws Exception {
//			       Signature signature = Signature.getInstance("SHA1withRSA");
//			       signature.initVerify(publicKey);
//			       signature.update(data);
//			       boolean verified = signature.verify(sign);
//			       if (verified==true) {
//			    	   System.out.println("This email is valid with its signature.");
//			       }else {
//			    	   System.out.println("This email is not valid, not compatible signature.");
//			       }
//			        return verified;
//			}	
		
		//Verifying the signature
		

		Certificate certificateUserA = keyStoreReader.getCertificateFromKeyStore(keyStoreUserB, KEY_STORE_USER_B_ALIAS);
		PublicKey publicKeyUserA = keyStoreReader.getPublicKeyFromCertificate(certificateUserA);
		
		Signature sign = Signature.getInstance("SHA1withRSA");

		//Calculating the signature
	      byte[] signature2 = sign.sign();      
	      
	      //Initializing the signature
	      sign.initVerify(publicKeyUserA);
	      //sign.update(bytes);
	      
	      //Verifying the signature
	      boolean bool = sign.verify(signature);
	      
	      if(bool) {
	         System.out.println("Signature verified");   
	      } else {
	         System.out.println("Signature failed");
	      }
		//
		
	    
        //TODO: Decrypt a message and decompress it. The private key is stored in a file.
		
//		
//		byte[] iv1 = JavaUtils.getBytesFromFile(IV1_FILE);
//		IvParameterSpec ivParameterSpec1 = new IvParameterSpec(iv1);
//		aesCipherDec.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec1);
//		
//		String str = MailHelper.getText(chosenMessage);
//		byte[] bodyEnc = Base64.decode(str);
//		
//		String receivedBodyTxt = new String(aesCipherDec.doFinal(bodyEnc));
//		String decompressedBodyText = GzipUtil.decompress(Base64.decode(receivedBodyTxt));
//		System.out.println("Body text: " + decompressedBodyText);
		
		
//		byte[] iv2 = JavaUtils.getBytesFromFile(IV2_FILE);
//		IvParameterSpec ivParameterSpec2 = new IvParameterSpec(iv2);
//		//inicijalizacija za dekriptovanje
//		aesCipherDec.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec2);
//		
//		//dekompresovanje i dekriptovanje subject-a
//		String decryptedSubjectTxt = new String(aesCipherDec.doFinal(Base64.decode(chosenMessage.getSubject())));
//		String decompressedSubjectTxt = GzipUtil.decompress(Base64.decode(decryptedSubjectTxt));
//		System.out.println("Subject text: " + new String(decompressedSubjectTxt));
	}
}
