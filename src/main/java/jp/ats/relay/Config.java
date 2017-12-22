package jp.ats.relay;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

public class Config {

	private final Properties properties;

	public Config(ResourceManager manager) {
		try (InputStream input = manager.load(Constants.RELAY_PROPERTIES)) {
			properties = new Properties();
			properties.load(new InputStreamReader(input, "UTF-8"));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * @return 次工程を起動する{@link NextCommand}の実装クラス
	 */
	public String getNextCommandClass() {
		return properties.getProperty("next-command-class");
	}

	/**
	 * @return プロジェクト名
	 */
	public String getProjectName() {
		return properties.getProperty("project-name");
	}

	/**
	 * @return メール送信用コマンド
	 */
	public String getMailSendCommand() {
		return properties.getProperty("mail-send-command");
	}

	/**
	 * @return エラーメール返信者
	 */
	public String getErrorMailFrom() {
		return properties.getProperty("error-mail-from");
	}

	/**
	 * @return {@link CommandExecutor}の実装クラス
	 */
	public String getCommandExecutorClass() {
		return properties.getProperty("command-executor-class");
	}

	/**
	 * @return システムエラー発生時の通知メールを使用するか
	 */
	public String usesSystemErrorMail() {
		return properties.getProperty("uses-system-error-mail");
	}

	/**
	 * @return システムエラー発生時の通知メールの宛先アドレス（スペース区切りで複数指定可能）
	 */
	public String getSystemErrorMailAdresses() {
		return properties.getProperty("system-error-mail-addresses");
	}

	/**
	 * @return 緊急停止指示フラグファイル
	 */
	public String getHaltFile() {
		return properties.getProperty("halt-file");
	}

	/**
	 * @return 実績評価用ファイル格納ディレクトリ
	 */
	public String getAssessmentDirectory() {
		return properties.getProperty("assessment-dir");
	}
}
