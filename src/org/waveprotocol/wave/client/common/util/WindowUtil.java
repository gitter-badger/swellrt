/*
 *
 * Copyright (C) 2007-2012 The kune development team (see CREDITS for details)
 * This file is part of kune.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.waveprotocol.wave.client.common.util;

import com.google.gwt.user.client.Window;

public class WindowUtil {

  public static WindowWrapper instance;

  /**
   * Displays a message in a modal dialog box.
   *
   * @param msg the message to be displayed.
   */
  public static void alert(String message) {
    if (instance == null) {
      Window.alert(message);
    } else {
      instance.alert(message);
    }
  }

  /**
   * Displays a request for information in a modal dialog box, along with the
   * standard 'OK' and 'Cancel' buttons.
   *
   * @param msg the message to be displayed
   * @param initialValue the initial value in the dialog's text field
   * @param callback the promt callback
   */
  public static void prompt(String msg, String initialValue, WindowPromptCallback callback) {
    if (instance == null) {
      callback.onReturn(Window.prompt(msg, initialValue));
    } else {
      instance.prompt(msg, initialValue, callback);
    }
  }

  /**
   * Displays a message in a modal dialog box, along with the standard 'OK' and
   * 'Cancel' buttons.
   *
   * @param msg the message to be displayed.
   * @param callback the confirm callback
   */
  public static void confirm(String msg, WindowConfirmCallback callback) {
    if (instance == null) {
      if (Window.confirm(msg)) {
        callback.onOk();
      }
      else {
        callback.onCancel();
      }
    } else {
      instance.confirm(msg, callback);
    }
  }
}
