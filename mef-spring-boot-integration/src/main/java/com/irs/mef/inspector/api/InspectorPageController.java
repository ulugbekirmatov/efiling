package com.irs.mef.inspector.api;

import com.irs.mef.inspector.ring.SendSnapshot;
import com.irs.mef.inspector.ring.SendSnapshotRing;
import com.irs.mef.inspector.ring.SnapshotId;
import com.irs.mef.inspector.ring.SnapshotSummary;
import com.irs.mef.inspector.view.InspectorHtml;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

@Controller
public class InspectorPageController {

    private final SendSnapshotRing ring;
    private final InspectorHtml html;

    public InspectorPageController(SendSnapshotRing ring, InspectorHtml html) {
        this.ring = ring;
        this.html = html;
    }

    /**
     * The operator's only inspector URL.
     * Missing id → newest snapshot (or empty state).
     * Unknown id → list + "not in the last N" pane. Never 404 the page; never hits IRS.
     */
    @GetMapping(value = "/inspector", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@RequestParam(required = false) String id) {
        List<SnapshotSummary> list = ring.listNewestFirst();
        Optional<String> requested = Optional.ofNullable(id).filter(s -> !s.isBlank());
        Optional<SendSnapshot> selected;
        if (requested.isPresent()) {
            selected = SnapshotId.tryParse(requested.get()).flatMap(ring::get);
        } else {
            selected = list.stream().findFirst().flatMap(s -> ring.get(s.submissionId()));
        }
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .contentType(MediaType.TEXT_HTML)
                .body(html.render(list, selected, requested));
    }
}
