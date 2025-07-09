package com.example;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

public class MyTabActionHandler extends EditorActionHandler {

    private final CopilotStartupActivity activity;
    private final EditorActionHandler originalHandler;

    public MyTabActionHandler(CopilotStartupActivity activity, EditorActionHandler originalHandler) {
        this.activity = activity;
        this.originalHandler = originalHandler;
    }

    @Override
    protected void doExecute(@NotNull Editor editor, Caret caret, DataContext dataContext) {
        String prediction = activity.getCurrentPrediction();
        if (prediction != null) {
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(editor.getProject(), () -> {
                int offset = editor.getCaretModel().getOffset();
                editor.getDocument().insertString(offset, prediction);
                editor.getCaretModel().moveToOffset(offset + prediction.length());
                activity.clearPrediction();
            });
        } else {
            if (originalHandler != null)
                originalHandler.execute(editor, caret, dataContext);
        }
    }
}

