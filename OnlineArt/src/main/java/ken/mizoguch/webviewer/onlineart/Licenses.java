/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.onlineart;

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
            + "limitations under the License.\n"
            + "\n"
            + "\n"
            + "* NRJavaSerial\n"
            + "Copyright 2017 Neuron Robotics\n"
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
            + "\n"
            + "* Apache Commons Net\n"
            + "Copyright 2001-2017 The Apache Software Foundation\n"
            + "\n"
            + "This product includes software developed at\n"
            + "The Apache Software Foundation (http://www.apache.org/).\n"
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
            + "\n"
            + "* Java Native Access\n"
            + "Java Native Access project (JNA) is dual-licensed under 2 \n"
            + "alternative Open Source/Free licenses: LGPL 2.1 or later and \n"
            + "Apache License 2.0. (starting with JNA version 4.0.0). \n"
            + "\n"
            + "You can freely decide which license you want to apply to \n"
            + "the project.\n"
            + "\n"
            + "You may obtain a copy of the LGPL License at:\n"
            + "\n"
            + "http://www.gnu.org/licenses/licenses.html\n"
            + "\n"
            + "A copy is also included in the downloadable source code package\n"
            + "containing JNA, in file \"LGPL2.1\", under the same directory\n"
            + "as this file.\n"
            + "\n"
            + "You may obtain a copy of the Apache License at:\n"
            + "\n"
            + "http://www.apache.org/licenses/\n"
            + "\n"
            + "A copy is also included in the downloadable source code package\n"
            + "containing JNA, in file \"AL2.0\", under the same directory\n"
            + "as this file.\n"
            + "\n"
            + "\n"
            + "* Java Native Access Platform\n"
            + "Java Native Access project (JNA) is dual-licensed under 2 \n"
            + "alternative Open Source/Free licenses: LGPL 2.1 or later and \n"
            + "Apache License 2.0. (starting with JNA version 4.0.0). \n"
            + "\n"
            + "You can freely decide which license you want to apply to \n"
            + "the project.\n"
            + "\n"
            + "You may obtain a copy of the LGPL License at:\n"
            + "\n"
            + "http://www.gnu.org/licenses/licenses.html\n"
            + "\n"
            + "A copy is also included in the downloadable source code package\n"
            + "containing JNA, in file \"LGPL2.1\", under the same directory\n"
            + "as this file.\n"
            + "\n"
            + "You may obtain a copy of the Apache License at:\n"
            + "\n"
            + "http://www.apache.org/licenses/\n"
            + "\n"
            + "A copy is also included in the downloadable source code package\n"
            + "containing JNA, in file \"AL2.0\", under the same directory\n"
            + "as this file.";

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
