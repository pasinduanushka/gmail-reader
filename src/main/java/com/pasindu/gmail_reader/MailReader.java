package com.pasindu.gmail_reader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.lang3.StringUtils;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;

/**
 * Mail Reader Class
 *
 */
public class MailReader {

	/**
	 * gmail username
	 */
	private static final String username = "*******@gmail.com";

	/**
	 * gmail password
	 */
	private static final String password = "********";

	/**
	 * main method
	 * 
	 * @param args
	 */
	public static void main(String[] args) {

		File attachmentsDir = new File("attachments");
		attachmentsDir.mkdirs();

		Properties properties = new Properties();
		// properties.put("mail.debug", "true");
		properties.put("mail.store.protocol", "imaps");
		properties.put("mail.imaps.host", "imap.gmail.com");
		properties.put("mail.imaps.port", "993");
		properties.put("mail.imaps.timeout", "10000");

		Session session = Session.getInstance(properties); // not
															// getDefaultInstance
		IMAPStore store = null;
		Folder inbox = null;

		try {
			store = (IMAPStore) session.getStore("imaps");
			store.connect(username, password);

			if (!store.hasCapability("IDLE")) {
				throw new RuntimeException("IDLE not supported");
			}

			inbox = (IMAPFolder) store.getFolder("INBOX");
			inbox.addMessageCountListener(new MessageCountAdapter() {

				@Override
				public void messagesAdded(MessageCountEvent event) {
					Message[] messages = event.getMessages();
					List<File> attachments = new ArrayList<File>();
					for (Message message : messages) {
						try {
							System.out.println("Mail Subject:- " + message.getSubject());

							System.out.println("Body:- " + getTextFromMessage(message));

							Multipart multipart = (Multipart) message.getContent();

							for (int i = 0; i < multipart.getCount(); i++) {
								BodyPart bodyPart = multipart.getBodyPart(i);
								if (!Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())
										&& !StringUtils.isNotBlank(bodyPart.getFileName())) {
									continue; // dealing with attachments only
								}
								InputStream is = bodyPart.getInputStream();
								System.out.println("Attachment:- " + bodyPart.getFileName());
								File f = new File("attachments\\" + bodyPart.getFileName());
								FileOutputStream fos = new FileOutputStream(f);
								byte[] buf = new byte[4096];
								int bytesRead;
								while ((bytesRead = is.read(buf)) != -1) {
									fos.write(buf, 0, bytesRead);
								}
								fos.close();
								attachments.add(f);
							}

						} catch (MessagingException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			});

			IdleThread idleThread = new IdleThread(inbox);
			idleThread.setDaemon(false);
			idleThread.start();

			idleThread.join();
			// idleThread.kill(); //to terminate from another thread

		} catch (Exception e) {
			e.printStackTrace();
		} finally {

			close(inbox);
			close(store);
		}
	}

	/**
	 * read text from message
	 * 
	 * @param message
	 * @return
	 */
	private static String getTextFromMessage(Message message) {
		String result = "";
		try {
			if (message.isMimeType("text/plain")) {
				result = message.getContent().toString();
			} else if (message.isMimeType("multipart/*")) {
				MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
				result = getTextFromMimeMultipart(mimeMultipart);
			}
		} catch (MessagingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * get text from multipart
	 * 
	 * @param mimeMultipart
	 * @return
	 * @throws Exception
	 */
	private static String getTextFromMimeMultipart(MimeMultipart mimeMultipart) throws Exception {
		String result = "";
		int count = mimeMultipart.getCount();
		for (int i = 0; i < count; i++) {
			BodyPart bodyPart = mimeMultipart.getBodyPart(i);
			if (bodyPart.isMimeType("text/plain")) {
				result = result + "\n" + bodyPart.getContent();
				break; // without break same text appears twice in my tests
			} else if (bodyPart.isMimeType("text/html")) {
				String html = (String) bodyPart.getContent();
				result = result + "\n" + org.jsoup.Jsoup.parse(html).text();
			} else if (bodyPart.getContent() instanceof MimeMultipart) {
				result = result + getTextFromMimeMultipart((MimeMultipart) bodyPart.getContent());
			}
		}
		return result;
	}

	/**
	 * thread to keep program idle
	 * 
	 * @author Pasindu
	 *
	 */
	private static class IdleThread extends Thread {
		private final Folder folder;
		private volatile boolean running = true;

		public IdleThread(Folder folder) {
			super();
			this.folder = folder;
		}

		@SuppressWarnings("unused")
		public synchronized void kill() {

			if (!running)
				return;
			this.running = false;
		}

		@Override
		public void run() {
			while (running) {

				try {
					ensureOpen(folder);
					System.out.println("enter idle");
					((IMAPFolder) folder).idle();
				} catch (Exception e) {
					// something went wrong
					// wait and try again
					e.printStackTrace();
					try {
						Thread.sleep(100);
					} catch (InterruptedException e1) {
						// ignore
					}
				}

			}
		}
	}

	/**
	 * close the folder
	 * 
	 * @param folder
	 */
	public static void close(final Folder folder) {
		try {
			if (folder != null && folder.isOpen()) {
				folder.close(false);
			}
		} catch (final Exception e) {
			// ignore
		}

	}

	/**
	 * close the store
	 * 
	 * @param store
	 */
	public static void close(final Store store) {
		try {
			if (store != null && store.isConnected()) {
				store.close();
			}
		} catch (final Exception e) {
			// ignore
		}

	}

	/**
	 * ensure the folder open
	 * 
	 * @param folder
	 * @throws MessagingException
	 */
	public static void ensureOpen(final Folder folder) throws MessagingException {

		if (folder != null) {
			Store store = folder.getStore();
			if (store != null && !store.isConnected()) {
				store.connect(username, password);
			}
		} else {
			throw new MessagingException("Unable to open a null folder");
		}

		if (folder.exists() && !folder.isOpen() && (folder.getType() & Folder.HOLDS_MESSAGES) != 0) {
			System.out.println("open folder " + folder.getFullName());
			folder.open(Folder.READ_ONLY);
			if (!folder.isOpen())
				throw new MessagingException("Unable to open folder " + folder.getFullName());
		}

	}
}
