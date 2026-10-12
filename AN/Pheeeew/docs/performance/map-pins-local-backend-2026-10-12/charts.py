#!/usr/bin/env python3
"""Render standalone SVG charts from validated profile summaries (requires reportlab)."""
import argparse
import csv
import json
from pathlib import Path

from reportlab.graphics import renderSVG
from reportlab.graphics.charts.lineplots import LinePlot
from reportlab.graphics.shapes import Drawing, Rect, String
from reportlab.lib import colors


def chart(title, subtitle, datasets, labels, palette, x_max, y_max, x_ticks, x_label, y_label):
    drawing = Drawing(1000, 560)
    drawing.add(Rect(0, 0, 1000, 560, fillColor=colors.HexColor("#ffffff"), strokeColor=None))
    drawing.add(String(60, 520, title, fontName="Helvetica-Bold", fontSize=22, fillColor=colors.HexColor("#142337")))
    drawing.add(String(60, 493, subtitle, fontName="Helvetica", fontSize=12, fillColor=colors.HexColor("#59687b")))
    plot = LinePlot()
    plot.x, plot.y, plot.width, plot.height = 90, 100, 820, 340
    plot.data = datasets
    plot.joinedLines = 1
    plot.xValueAxis.valueMin, plot.xValueAxis.valueMax = 0, x_max
    plot.xValueAxis.valueSteps = x_ticks
    plot.yValueAxis.valueMin, plot.yValueAxis.valueMax = 0, y_max
    plot.yValueAxis.visibleGrid = True
    plot.yValueAxis.gridStrokeColor = colors.HexColor("#e6edf4")
    plot.xValueAxis.labels.fontName = plot.yValueAxis.labels.fontName = "Helvetica"
    for index, color in enumerate(palette):
        plot.lines[index].strokeColor = colors.HexColor(color)
        plot.lines[index].strokeWidth = 2.5
    drawing.add(plot)
    drawing.add(String(490, 56, x_label, fontName="Helvetica", fontSize=12, textAnchor="middle"))
    drawing.add(String(60, 460, y_label, fontName="Helvetica", fontSize=12))
    for index, (label, color) in enumerate(zip(labels, palette)):
        x = 90 + index * 175
        drawing.add(Rect(x, 18, 18, 4, fillColor=colors.HexColor(color), strokeColor=None))
        drawing.add(String(x + 25, 15, label, fontName="Helvetica", fontSize=10))
    return drawing


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    summary = json.loads((args.directory / "summary.json").read_text())
    fixtures = sorted((row for row in summary if row["source"] == "backend_local"), key=lambda row: row["target_count"])
    counts = [0, 50, 100, 200, 400, 800, 1600]
    fixtures = [row for row in fixtures if row["target_count"] in counts]
    datasets = [[(row["target_count"], row[field]) for row in fixtures] for field in ["total_p50_ms", "total_p95_ms"]]
    drawing = chart(
        "Pins vs request-to-complete time", "Galaxy S23 FE / Android 15 / 30 planned trials per count (successes only) / local Spring Boot + PostGIS over USB forwarding",
        datasets, ["Total median", "Total P95"], ["#1778c8", "#e1812c"],
        1600, max(value for dataset in datasets for _, value in dataset) * 1.12,
        [0, 200, 400, 800, 1200, 1600], "Target pins (200 pins per page)", "Elapsed time (ms)",
    )
    renderSVG.drawToFile(drawing, str(args.directory / "latency-by-count.svg"))
    with (args.directory / "time-counts.csv").open() as stream:
        rows = list(csv.DictReader(stream))
    selected = [200, 400, 800, 1600]
    datasets = [[(0, 0)] + [(int(row["elapsed_ms"]), float(row["mean_visible_count"])) for row in rows
                           if row["source"] == "backend_local" and row["target_count"] == str(count) and int(row["elapsed_ms"]) <= 2000]
                for count in selected]
    drawing = chart(
        "Visible pins over elapsed time", "Mean visible feature count across successful trials (29-30/count); observations at 100, 250, 500, 1000 and 2000 ms",
        datasets, [f"{count} target pins" for count in selected], ["#1778c8", "#29a175", "#e1812c", "#a157ca"],
        2000, 1700, [0, 250, 500, 1000, 1500, 2000], "Time after first request (ms)", "Visible feature IDs",
    )
    renderSVG.drawToFile(drawing, str(args.directory / "visible-count-by-time.svg"))


if __name__ == "__main__":
    main()
