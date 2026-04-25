package org.waveprotocol.box.j2cl.compose;

import elemental2.dom.DomGlobal;
import elemental2.dom.Event;
import elemental2.dom.File;
import elemental2.dom.FileList;
import elemental2.dom.HTMLFormElement;
import elemental2.dom.HTMLInputElement;
import elemental2.dom.HTMLElement;
import elemental2.dom.HTMLTextAreaElement;
import java.util.ArrayList;
import java.util.List;
import jsinterop.base.Js;

public final class J2clComposeSurfaceView implements J2clComposeSurfaceController.View {
  private final HTMLTextAreaElement createInput;
  private final HTMLElement createSubmit;
  private final HTMLElement replyElement;
  private final HTMLInputElement attachmentInput;
  private J2clComposeSurfaceController.Listener listener;

  public J2clComposeSurfaceView(HTMLElement createHost, HTMLElement replyHost) {
    createHost.innerHTML = "";
    replyHost.innerHTML = "";

    HTMLElement shell = (HTMLElement) DomGlobal.document.createElement("composer-shell");
    createHost.appendChild(shell);

    HTMLFormElement createForm = (HTMLFormElement) DomGlobal.document.createElement("form");
    createForm.setAttribute("slot", "create");
    createForm.className = "j2cl-compose-create-form";
    shell.appendChild(createForm);

    createInput = (HTMLTextAreaElement) DomGlobal.document.createElement("textarea");
    createInput.setAttribute("aria-label", "New wave content");
    createInput.setAttribute("placeholder", "Start a new wave");
    createInput.rows = 4;
    createInput.oninput =
        event -> {
          if (listener != null) {
            listener.onCreateDraftChanged(createInput.value);
          }
          return null;
        };
    createForm.appendChild(createInput);

    createSubmit = (HTMLElement) DomGlobal.document.createElement("composer-submit-affordance");
    setProperty(createSubmit, "label", "Create wave");
    createForm.appendChild(createSubmit);
    createSubmit.addEventListener(
        "submit-affordance",
        event -> {
          if (listener != null) {
            listener.onCreateSubmitted(createInput.value);
          }
        });

    replyElement = (HTMLElement) DomGlobal.document.createElement("composer-inline-reply");
    replyHost.appendChild(replyElement);
    replyElement.addEventListener(
        "draft-change",
        event -> {
          if (listener != null) {
            listener.onReplyDraftChanged(eventDetailValue(event));
          }
        });
    replyElement.addEventListener(
        "reply-submit",
        event -> {
          if (listener != null) {
            listener.onReplySubmitted(propertyString(replyElement, "draft"));
          }
        });
    replyElement.addEventListener(
        "attachment-paste-image",
        event -> {
          if (listener != null) {
            listener.onPastedImage(eventDetailProperty(event, "file"));
          }
        });

    attachmentInput = (HTMLInputElement) DomGlobal.document.createElement("input");
    attachmentInput.type = "file";
    attachmentInput.multiple = true;
    attachmentInput.setAttribute("aria-label", "Choose files to attach");
    attachmentInput.setAttribute("hidden", "");
    // Keep the programmatic picker from joining any ancestor form submission.
    attachmentInput.setAttribute("form", "j2cl-detached-attachment-picker");
    replyHost.appendChild(attachmentInput);
    attachmentInput.onchange =
        event -> {
          if (listener != null) {
            listener.onAttachmentFilesSelected(fileSelections(attachmentInput.files));
          }
          attachmentInput.value = "";
          return null;
        };
  }

  @Override
  public void bind(J2clComposeSurfaceController.Listener listener) {
    this.listener = listener;
  }

  @Override
  public void render(J2clComposeSurfaceModel model) {
    createInput.value = model.getCreateDraft();
    createInput.disabled = !model.isCreateEnabled() || model.isCreateSubmitting();
    setProperty(createSubmit, "busy", model.isCreateSubmitting());
    setProperty(createSubmit, "disabled", !model.isCreateEnabled() || model.isCreateSubmitting());
    setProperty(createSubmit, "status", model.getCreateStatusText());
    setProperty(createSubmit, "error", model.getCreateErrorText());

    setProperty(replyElement, "available", model.isReplyAvailable());
    setProperty(replyElement, "targetLabel", model.getReplyTargetLabel());
    setProperty(replyElement, "draft", model.getReplyDraft());
    setProperty(replyElement, "submitting", model.isReplySubmitting());
    setProperty(replyElement, "staleBasis", model.isReplyStaleBasis());
    setProperty(replyElement, "status", model.getReplyStatusText());
    setProperty(replyElement, "error", model.getReplyErrorText());
    setProperty(replyElement, "activeCommand", model.getActiveCommandId());
    setProperty(replyElement, "commandStatus", model.getCommandStatusText());
    setProperty(replyElement, "commandError", model.getCommandErrorText());
  }

  @Override
  public void openAttachmentPicker() {
    attachmentInput.click();
  }

  @Override
  public void focusReplyComposer() {
    replyElement.dispatchEvent(new Event("composer-focus-request"));
  }

  private static void setProperty(HTMLElement element, String name, Object value) {
    Js.asPropertyMap(element).set(name, value);
  }

  private static String propertyString(HTMLElement element, String name) {
    Object value = Js.asPropertyMap(element).get(name);
    return value == null ? "" : String.valueOf(value);
  }

  private static String eventDetailValue(Event event) {
    Object detail = Js.asPropertyMap(event).get("detail");
    if (detail == null) {
      return "";
    }
    Object value = Js.asPropertyMap(detail).get("value");
    return value == null ? "" : String.valueOf(value);
  }

  private static Object eventDetailProperty(Event event, String propertyName) {
    Object detail = Js.asPropertyMap(event).get("detail");
    return detail == null ? null : Js.asPropertyMap(detail).get(propertyName);
  }

  private static List<J2clComposeSurfaceController.AttachmentFileSelection> fileSelections(
      FileList files) {
    List<J2clComposeSurfaceController.AttachmentFileSelection> selections =
        new ArrayList<J2clComposeSurfaceController.AttachmentFileSelection>();
    if (files == null) {
      return selections;
    }
    for (int i = 0; i < files.length; i++) {
      File file = files.item(i);
      if (file != null) {
        selections.add(new J2clComposeSurfaceController.AttachmentFileSelection(file, file.name));
      }
    }
    return selections;
  }
}
