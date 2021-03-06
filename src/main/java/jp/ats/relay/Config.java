package jp.ats.relay;

import java.util.Properties;

class Config {

	private final Properties properties;

	public Config(Properties properties) {
		this.properties = properties;
	}

	public Config() {
		properties = Shell.config(Constants.CONFIG_PROPERTIES);
	}

	/**
	 * @return 次工程を起動する{@link NextCommand}の実装クラス
	 */
	String getNextCommandClass() {
		return properties.getProperty("next-command-class");
	}

	/**
	 * @return プロジェクト名
	 */
	String getProjectName() {
		return properties.getProperty("project-name");
	}

	/**
	 * @return メール送信用コマンド
	 */
	String getMailSendCommand() {
		return properties.getProperty("mail-send-command");
	}

	/**
	 * @return エラーメール返信者
	 */
	String getErrorMailFrom() {
		return properties.getProperty("error-mail-from");
	}

	/**
	 * @return {@link CommandExecutor}の実装クラス
	 */
	String getCommandExecutorClass() {
		return properties.getProperty("command-executor-class");
	}

	/**
	 * @return システムエラー発生時の通知メールを使用するか
	 */
	String usesSystemErrorMail() {
		return properties.getProperty("uses-system-error-mail");
	}

	/**
	 * @return システムエラー発生時の通知メールの宛先アドレス（スペース区切りで複数指定可能）
	 */
	String getSystemErrorMailAdresses() {
		return properties.getProperty("system-error-mail-addresses");
	}

	/**
	 * @return 緊急停止指示フラグファイル
	 */
	String getHaltFile() {
		return properties.getProperty("halt-file");
	}

	/**
	 * @return 実績評価用ファイル格納ディレクトリ
	 */
	String getAssessmentDirectory() {
		return properties.getProperty("assessment-dir");
	}
}
