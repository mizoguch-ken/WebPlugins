/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.db;

import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;

/**
 *
 * @author mizoguch-ken
 */
public class Licenses {

    private final String LICENSES
            = "*** Third Party Licenses ***\n"
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
            + "limitations under the License.";

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
