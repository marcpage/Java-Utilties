import javax.swing.JTextField;
import javax.swing.JComponent;
import javax.swing.InputVerifier;
import java.awt.Color;

/** Validates a text field against perforce @revisions
	Labels are not supported.
*/
public class FormattedTextFieldPerforceAtRevisionsVerifier extends InputVerifier {
	/** Create a verifier with red and green bad and good respectively backgrounds.
	*/
	public FormattedTextFieldPerforceAtRevisionsVerifier() {
		_good= Color.green;
		_bad= Color.red;
	}
	/** Create a verifier.
		@param good	The color to set the background when the value is good
		@param bad	The color to set the background when the value is bad
	*/
	public FormattedTextFieldPerforceAtRevisionsVerifier(Color good, Color bad) {
		_good= good;
		_bad= bad;
	}
	/** Workhorse function.
		Valid field values are:
			<br> yyyy/mm/dd
			<br> yyyy/mm/dd:hh:mm:ss
			<br> [changelist#]
		<br> If the text field is not valid, the background color will change the _bad
			When it is good, the background color will change to _good
		@param input	The JFormattedTextField to validate
	*/
	public boolean verify(JComponent input) {
		if(input instanceof JTextField) {
			JTextField	field= (JTextField)input;
			String		contents= field.getText();

			if(contents.matches("^([0-9]+|([12][09][0-9][0-9]/[01][0-9]/[0-3][0-9](:[0-2][0-9]:[0-6][0-9]:[0-6][0-9])?))$")) {
				field.setBackground(Color.green);
			} else {
				field.setBackground(Color.red);
				return false;
			}
		}
		return true;
	}
	/** Background color for good data */
	private Color	_good;
	/** Background color for bad data */
	private Color	_bad;
	/** Test.
		@param args	ignored
	*/
	public static void main(String... args) {
		javax.swing.JFrame			frame= new javax.swing.JFrame();
		JTextField		text1= new JTextField("sync date (yyyy/mm/dd or yyyy/mm/dd:hh:mm:ss)/changelist");
		JTextField		text2= new JTextField("sync date (yyyy/mm/dd or yyyy/mm/dd:hh:mm:ss)/changelist");

		frame.getContentPane().add(text1, java.awt.BorderLayout.NORTH);
		text1.setInputVerifier(new FormattedTextFieldPerforceAtRevisionsVerifier());
		frame.getContentPane().add(text2, java.awt.BorderLayout.SOUTH);
		text2.setInputVerifier(new FormattedTextFieldPerforceAtRevisionsVerifier());
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			public void windowClsoing(java.awt.event.WindowEvent e) {
				System.exit(0);
			}
		});
		frame.pack();
		frame.setVisible(true);
	}
}
