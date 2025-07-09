package com.example;

import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import java.awt.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;

public class CopilotStartupActivity implements StartupActivity {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledTask;
    private Inlay<?> currentInlay;
    private String currentPrediction;


    @Override
    public void runActivity(@NotNull Project project) {
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {

                ApplicationManager.getApplication().invokeLater(() -> {
                    if (currentInlay != null) {
                        clearPrediction();
                    }
                });

                if (scheduledTask != null && !scheduledTask.isDone()) {
                    scheduledTask.cancel(false);
                }

                scheduledTask = scheduler.schedule(() -> {
                    int curOffset = event.getOffset();
                    Document document = event.getDocument();
                    String contentUpToCursor = document.getText().substring(0, curOffset);

                    String systemPrompt = "You are an expert software engineer helping with code completion. \n" +
                            "Given the following code context, predict the most likely next few lines of code (maximum of 10 lines) that would logically follow.\n" +
                            "\n" +
                            "Rules:\n" +
                            "1. Only provide the next few lines of code, not explanations\n" +
                            "2. Maintain proper indentation and formatting\n" +
                            "3. Consider the programming language and context\n" +
                            "4. If you cannot make a confident prediction, return empty string\n" +
                            "5. Do not include any markdown formatting or code blocks\n" +
                            "6. Focus on completing the immediate next logical lines\n" +
                            "6. Don't think long time, give the prediction very fast\n";

                    try {
                        Client client = Client.builder()
                                .apiKey("")
                                .build();

                        GenerateContentConfig config = GenerateContentConfig.builder()
                                .systemInstruction(Content.builder().role("system").parts(Part.fromText(systemPrompt)))
                                .build();

                        GenerateContentResponse response = client.models.generateContent(
                                "gemini-2.5-flash",
                                contentUpToCursor,
                                config
                        );
                        
                        ApplicationManager.getApplication().invokeLater(() -> {
                            Editor[] editors = EditorFactory.getInstance().getEditors(event.getDocument(), project);
                            if (editors.length == 0) return;

                            Editor editor = editors[0];
                            int offset = editor.getCaretModel().getOffset();

                            clearPrediction();

                            currentPrediction = response.text();
                            InlayModel inlayModel = editor.getInlayModel();

                            currentInlay = inlayModel.addAfterLineEndElement(offset, false, new EditorCustomElementRenderer() {
                                @Override
                                public int calcWidthInPixels(@NotNull Inlay inlay) {
                                    FontMetrics metrics = editor.getContentComponent().getFontMetrics(editor.getColorsScheme().getFont(EditorFontType.PLAIN));
                                    return metrics.stringWidth(currentPrediction);
                                }

                                @Override
                                public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle targetRegion, @NotNull TextAttributes textAttributes) {
                                    g.setColor(new Color(128, 128, 128, 128));
                                    g.setFont(editor.getColorsScheme().getFont(EditorFontType.PLAIN));
                                    g.drawString(currentPrediction, targetRegion.x, targetRegion.y + g.getFontMetrics().getAscent());
                                }
                            });
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, 2, TimeUnit.SECONDS);
            }
        }, project);

        EditorActionManager manager = EditorActionManager.getInstance();
        EditorActionHandler originalHandler = manager.getActionHandler(IdeActions.ACTION_EDITOR_TAB);
        manager.setActionHandler(IdeActions.ACTION_EDITOR_TAB, new MyTabActionHandler(this, originalHandler));
    }

    public void clearPrediction() {
        if (currentInlay != null) {
            Disposer.dispose(currentInlay);
            currentInlay = null;
        }
        currentPrediction = null;
    }

    public String getCurrentPrediction() {
        return currentPrediction;
    }

}
