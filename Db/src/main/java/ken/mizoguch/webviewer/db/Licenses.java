package ken.mizoguch.webviewer.db;

import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;

/**
 *
 * @author mizoguch-ken
 */
public class Licenses {

    private final String LICENSES = "*** Third Party Licenses ***\n"
            + "\n"
            + "* Gson\n"
            + "Copyright 2008 Google Inc.\n"
            + "\n"
            + "Licensed under the Apache License, Version 2.0 (the \"License\");\n"
            + "you may not use this file except in compliance with the License.\n"
            + "You may obtain a copy of the License at\n"
            + "\n"
            + "    http://www.apache.org/licenses/LICENSE-2.0\n"
            + "\n"
            + "Unless required by applicable law or agreed to in writing, software\n"
            + "distributed under the License is distributed on an \"AS IS\" BASIS,\n"
            + "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
            + "See the License for the specific language governing permissions and\n"
            + "limitations under the License.\n"
            + "\n"
            + "* SLF4J\n"
            + "Copyright (c) 2004-2022 QOS.ch Sarl (Switzerland)\n" + //
            "All rights reserved.\n" + //
            "\n" + //
            "Permission is hereby granted, free  of charge, to any person obtaining\n" + //
            "a  copy  of this  software  and  associated  documentation files  (the\n" + //
            "\"Software\"), to  deal in  the Software without  restriction, including\n" + //
            "without limitation  the rights to  use, copy, modify,  merge, publish,\n" + //
            "distribute,  sublicense, and/or sell  copies of  the Software,  and to\n" + //
            "permit persons to whom the Software  is furnished to do so, subject to\n" + //
            "the following conditions:\n" + //
            "\n" + //
            "The  above  copyright  notice  and  this permission  notice  shall  be\n" + //
            "included in all copies or substantial portions of the Software.\n" + //
            "\n" + //
            "THE  SOFTWARE IS  PROVIDED  \"AS  IS\", WITHOUT  WARRANTY  OF ANY  KIND,\n" + //
            "EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF\n" + //
            "MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND\n" + //
            "NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE\n" + //
            "LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION\n" + //
            "OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION\n" + //
            "WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.";

    public void show() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        TextArea textAera = new TextArea();

        textAera.setText(LICENSES);
        alert.setResizable(true);
        alert.setTitle("Licenses");
        alert.getDialogPane().setHeaderText(null);
        textAera.setEditable(false);
        textAera.setWrapText(true);
        alert.getDialogPane().setContent(textAera);
        alert.show();
    }
}
