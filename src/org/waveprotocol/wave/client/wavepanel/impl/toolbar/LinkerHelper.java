/**
 * Copyright 2012 Apache Wave
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.waveprotocol.wave.client.wavepanel.impl.toolbar;

import org.waveprotocol.wave.client.common.util.WindowPromptCallback;
import org.waveprotocol.wave.client.common.util.WindowUtil;
import org.waveprotocol.wave.client.doodad.link.Link;
import org.waveprotocol.wave.client.doodad.link.Link.InvalidLinkException;
import org.waveprotocol.wave.client.editor.EditorContext;
import org.waveprotocol.wave.client.editor.util.EditorAnnotationUtil;
import org.waveprotocol.wave.model.document.util.DocHelper;
import org.waveprotocol.wave.model.document.util.FocusedRange;
import org.waveprotocol.wave.model.document.util.Range;

/**
 * The Class LinkerHelper is a utility for manage (create, remove, etc) links
 * while editing a document via the toolbar or via shortcuts
 */
public class LinkerHelper {

  /**
   * Helper for insert links while editing a document
   *
   * @param editor the wave editor
   */
  public static void onCreateLink(final EditorContext editor) {
    FocusedRange range = editor.getSelectionHelper().getSelectionRange();
    if (range == null || range.isCollapsed()) {
      WindowUtil.alert("Select some text to create a link.");
      return;
    }
    try {
      // We try to create a link with the current selection, if fails, we ask
      // for a link
      Range rg = range.asRange();
      String text = DocHelper.getText(editor.getDocument(), rg.getStart(), rg.getEnd());
      String linkAnnotationValue = Link.normalizeLink(text);
      EditorAnnotationUtil.setAnnotationOverSelection(editor, Link.KEY, linkAnnotationValue);
    } catch (InvalidLinkException e) {
          WindowUtil.prompt("Enter link URL", "http://",
              new WindowPromptCallback() {
                @Override
                public void onReturn(String rawLinkValue) {
                  // user hit "ESC" or "cancel"
                  if (rawLinkValue == null) {
                    return;
                  }
                  try {
                    String linkAnnotationValue = Link.normalizeLink(rawLinkValue);
                    EditorAnnotationUtil.setAnnotationOverSelection(editor, Link.KEY, linkAnnotationValue);
                  } catch (InvalidLinkException e2) {
                    WindowUtil.alert(e2.getLocalizedMessage());
                  }
                }}
              );
    }
  }

  /**
   * Helper for remove links while editing a document
   *
   * @param editor the wave editor
   */
  public static void onClearLink(EditorContext editor) {
    if (editor.getSelectionHelper().getSelectionRange() != null) {
      EditorAnnotationUtil.clearAnnotationsOverSelection(editor, Link.LINK_KEYS);
    }
  }
}
