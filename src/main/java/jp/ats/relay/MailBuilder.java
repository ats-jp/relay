package jp.ats.relay;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import javax.mail.util.ByteArrayDataSource;

public class MailBuilder {

	public static final Charset CHARSET = StandardCharsets.UTF_8;

	private final MimeMessage message;

	private final List<InternetAddress> to = new LinkedList<InternetAddress>();

	private final List<InternetAddress> cc = new LinkedList<InternetAddress>();

	private final List<InternetAddress> bcc = new LinkedList<InternetAddress>();

	private String content;

	private String contentType;

	private DataSource attachment;

	private String attachmentName;

	/**
	 * DB接続ができない環境でのインスタンスを生成
	 * @return MailBuilder
	 */
	public static MailBuilder getInstance() {
		return new MailBuilder(new MimeMessage(Session.getDefaultInstance(System.getProperties(), null)));
	}

	public MailBuilder(MimeMessage message) {
		this.message = message;
	}

	public void addMailTo(String address) throws AddressException {
		addMailTo(new InternetAddress(address));
	}

	public void addCC(String address) throws AddressException {
		addCC(new InternetAddress(address));
	}

	public void addBCC(String address) throws AddressException {
		addBCC(new InternetAddress(address));
	}

	public void setFrom(String address) throws MessagingException {
		setFrom(new InternetAddress(address));
	}

	public void addMailTo(InternetAddress address) {
		to.add(address);
	}

	public void addCC(InternetAddress address) {
		cc.add(address);
	}

	public void addBCC(InternetAddress address) {
		bcc.add(address);
	}

	public void setFrom(InternetAddress address) throws MessagingException {
		message.setFrom(address);
	}

	public void addHeader(String name, String value) throws MessagingException {
		message.addHeader(name, value);
	}

	public void setSubject(String subject) throws MessagingException {
		try {
			message.setSubject(
				MimeUtility.encodeText(
					subject,
					CHARSET.name(),
					"B"));
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e.getMessage());
		}
	}

	public void setMessage(String message) {
		content = message;
		contentType = "text/plain; charset=\"" + CHARSET.name() + "\"";
	}

	public void setHtmlMessage(String message) {
		content = message;
		contentType = "text/html; charset=\"" + CHARSET.name() + "\"";
	}

	public void attach(byte[] data, String contentType, String attachmentName) {
		attachment = new ByteArrayDataSource(data, contentType);
		this.attachmentName = attachmentName;
	}

	public byte[] buildHeader() throws MessagingException {
		prepareRecipients();

		ByteArrayOutputStream output = new ByteArrayOutputStream();

		toList(message.getAllHeaderLines()).forEach(header -> {
			byte[] bytes = header.getBytes(StandardCharsets.UTF_8);
			output.write(bytes, 0, bytes.length);
			output.write('\r');
			output.write('\n');
		});

		return output.toByteArray();
	}

	public byte[] build() throws MessagingException, IOException {
		prepareRecipients();

		if (attachment == null) {
			setMessageOnly();
		} else {
			setMessageAndFile();
		}

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		message.writeTo(output);

		return output.toByteArray();
	}

	@SuppressWarnings("unchecked")
	private static List<String> toList(Enumeration<?> headers) {
		return Collections.list((Enumeration<String>) headers);
	}

	private void prepareRecipients() throws MessagingException {
		InternetAddress[] tos = to.toArray(new InternetAddress[to.size()]);
		InternetAddress[] ccs = cc.toArray(new InternetAddress[cc.size()]);
		InternetAddress[] bccs = bcc.toArray(new InternetAddress[bcc.size()]);

		message.setRecipients(Message.RecipientType.TO, tos);
		if (ccs.length > 0) message.setRecipients(Message.RecipientType.CC, ccs);
		if (bccs.length > 0) message.setRecipients(
			Message.RecipientType.BCC,
			bccs);
	}

	private void setMessageOnly() throws MessagingException {
		message.setContent(content, contentType);
	}

	private void setMessageAndFile() throws MessagingException {
		MimeMultipart multi = new MimeMultipart();

		MimeBodyPart text = new MimeBodyPart();
		text.setContent(content, contentType);
		multi.addBodyPart(text);

		MimeBodyPart attach = new MimeBodyPart();
		DataHandler handler = new DataHandler(attachment);
		attach.setDataHandler(handler);

		if (attachmentName != null && !attachmentName.equals("")) attach.setFileName(attachmentName);
		multi.addBodyPart(attach);

		message.setContent(multi);
	}
}
