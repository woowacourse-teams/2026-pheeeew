package com.pheeeew.sigh.experiment.e001;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class E001Csv {

    private E001Csv() {
    }

    static List<List<String>> read(String text) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean closed = false;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (quoted) {
                if (value == '"') {
                    if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
                        cell.append('"');
                        index++;
                    } else {
                        quoted = false;
                        closed = true;
                    }
                } else {
                    cell.append(value);
                }
            } else if (value == ',' || value == '\n' || value == '\r') {
                row.add(cell.toString());
                cell.setLength(0);
                closed = false;
                if (value != ',') {
                    if (value == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                        index++;
                    }
                    rows.add(List.copyOf(row));
                    row.clear();
                }
            } else if (value == '"' && cell.isEmpty() && !closed) {
                quoted = true;
            } else if (closed || value == '"') {
                throw new IOException("CSV 따옴표 형식이 유효하지 않아요.");
            } else {
                cell.append(value);
            }
        }
        if (quoted) {
            throw new IOException("CSV 따옴표가 닫히지 않았어요.");
        }
        if (!row.isEmpty() || !cell.isEmpty() || closed) {
            row.add(cell.toString());
            rows.add(List.copyOf(row));
        }
        return List.copyOf(rows);
    }
}
