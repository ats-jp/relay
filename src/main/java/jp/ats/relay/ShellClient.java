package jp.ats.relay;

/**
 * DBのトランザクションで保護された処理範囲を表すインターフェイスです。
 */
public interface ShellClient {

	/**
	 * トランザクション内で行うべき処理を実装すること<br>
	 * execute()内では<br>
	 * {@link Shell#transaction()}<br>
	 * {@link Shell#config()}<br>
	 * {@link Shell#args()}<br>
	 * を使用することが可能
	 */
	void execute();

	/**
	 * このクライアントがデータベースを利用するかを返します。
	 * @return usesDataBase
	 */
	boolean usesDatabase();

	default void start() {
		Shell shell = new Shell(this);
		shell.setRunnable(() -> execute());
		shell.run();
	};
}
