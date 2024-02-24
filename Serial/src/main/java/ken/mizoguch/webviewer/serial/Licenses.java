package ken.mizoguch.webviewer.serial;

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
            + "\n"
            + "* NRJavaSerial\n"
            + "Copyright 2018 Neuron Robotics\n"
            + "\n"
            + "RXTX License v 2.1 - LGPL v 2.1 + Linking Over Controlled Interface.\n"
            + "RXTX is a native interface to serial ports in java.\n"
            + "\n"
            + "A copy of the LGPL v 2.1 may be found at\n"
            + "http://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt. \n"
            + "A copy is here for your convenience.\n"
            + "\n"
            + "This library is free software; you can redistribute it and/or\n"
            + "modify it under the terms of the GNU Lesser General Public\n"
            + "License as published by the Free Software Foundation; either\n"
            + "version 2.1 of the License, or (at your option) any later version.\n"
            + "\n"
            + "This library is distributed in the hope that it will be useful,\n"
            + "but WITHOUT ANY WARRANTY; without even the implied warranty of\n"
            + "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU\n"
            + "Lesser General Public License for more details.\n"
            + "\n"
            + "An executable that contains no derivative of any portion of RXTX, but\n"
            + "is designed to work with RXTX by being dynamically linked with it,\n"
            + "is considered a \"work that uses the Library\" subject to the terms and\n"
            + "conditions of the GNU Lesser General Public License.\n"
            + "\n"
            + "The following has been added to the RXTX License to remove\n"
            + "any confusion about linking to RXTX.   We want to allow in part what\n"
            + "section 5, paragraph 2 of the LGPL does not permit in the special\n"
            + "case of linking over a controlled interface.  The intent is to add a\n"
            + "Java Specification Request or standards body defined interface in the\n"
            + "future as another exception but one is not currently available.\n"
            + "\n"
            + "http://www.fsf.org/licenses/gpl-faq.html#LinkingOverControlledInterface\n"
            + "\n"
            + "As a special exception, the copyright holders of RXTX give you\n"
            + "permission to link RXTX with independent modules that communicate with\n"
            + "RXTX solely through the Sun Microsytems CommAPI interface version 2,\n"
            + "regardless of the license terms of these independent modules, and to copy\n"
            + "and distribute the resulting combined work under terms of your choice,\n"
            + "provided that every copy of the combined work is accompanied by a complete\n"
            + "copy of the source code of RXTX (the version of RXTX used to produce the\n"
            + "combined work), being distributed under the terms of the GNU Lesser General\n"
            + "Public License plus this exception.  An independent module is a\n"
            + "module which is not derived from or based on RXTX.\n"
            + "\n"
            + "Note that people who make modified versions of RXTX are not obligated\n"
            + "to grant this special exception for their modified versions; it is\n"
            + "their choice whether to do so.  The GNU Lesser General Public License\n"
            + "gives permission to release a modified version without this exception; this\n"
            + "exception also makes it possible to release a modified version which\n"
            + "carries forward this exception.\n"
            + "\n"
            + "You should have received a copy of the GNU Lesser General Public\n"
            + "License along with this library; if not, write to the Free\n"
            + "Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA\n"
            + "All trademarks belong to their respective owners.\n"
            + "\n"
            + "\n"
            + "* Apache Commons Net\n"
            + "Copyright 2001-2023 The Apache Software Foundation\n"
            + "\n"
            + "This product includes software developed at\n"
            + "The Apache Software Foundation (https://www.apache.org/).\n"
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
