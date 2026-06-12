package dev.chronica;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ProceduralHistoryWriter {
    public enum Style { EPIC, SCHOLARLY, TRAGIC }

    private final NameGenerator nameGenerator;
    private final Style style;

    public ProceduralHistoryWriter(NameGenerator nameGenerator, Style style) {
        this.nameGenerator = nameGenerator;
        this.style = style;
    }

    public List<String> generateBookPages(List<HistoryEvent> events) {
        List<String> pages = new ArrayList<>();
        List<HistoryEvent> ordered = events.stream()
                .sorted(Comparator.comparingLong(HistoryEvent::worldTime))
                .toList();
        StringBuilder page = new StringBuilder();
        for (HistoryEvent event : ordered) {
            String paragraph = paragraph(event);
            if (page.length() + paragraph.length() > 780) {
                pages.add(page.toString().trim());
                page = new StringBuilder();
            }
            page.append(paragraph).append("\n\n");
        }
        if (!page.isEmpty()) pages.add(page.toString().trim());
        if (pages.isEmpty()) pages.add("No chronicle has yet been written for this land.");
        return pages;
    }

    private String paragraph(HistoryEvent event) {
        String lore = event.generateLoreText(nameGenerator);
        return switch (style) {
            case EPIC -> "Let it be sung: " + lore + " The roads remember the iron of that hour.";
            case SCHOLARLY -> "Record " + event.worldTime() + ": " + lore + " This account is preserved without embellishment.";
            case TRAGIC -> "The ink darkens around this memory. " + lore + " Few witnesses remained to speak of it.";
        };
    }
}
