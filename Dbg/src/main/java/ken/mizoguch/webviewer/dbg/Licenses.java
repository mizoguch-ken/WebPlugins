/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.dbg;

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
            + "* Firebug Lite\n"
            + "Software License Agreement (BSD License)\n"
            + "\n"
            + "Copyright (c) 2007, Parakey Inc.\n"
            + "All rights reserved.\n"
            + "\n"
            + "Redistribution and use of this software in source and binary forms, with or without modification,\n"
            + "are permitted provided that the following conditions are met:\n"
            + "\n"
            + "* Redistributions of source code must retain the above\n"
            + "  copyright notice, this list of conditions and the\n"
            + "  following disclaimer.\n"
            + "\n"
            + "* Redistributions in binary form must reproduce the above\n"
            + "  copyright notice, this list of conditions and the\n"
            + "  following disclaimer in the documentation and/or other\n"
            + "  materials provided with the distribution.\n"
            + "\n"
            + "* Neither the name of Parakey Inc. nor the names of its\n"
            + "  contributors may be used to endorse or promote products\n"
            + "  derived from this software without specific prior\n"
            + "  written permission of Parakey Inc.\n"
            + "\n"
            + "THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\" AND ANY EXPRESS OR\n"
            + "IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND\n"
            + "FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR\n"
            + "CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL\n"
            + "DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,\n"
            + "DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER\n"
            + "IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT\n"
            + "OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.";

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
