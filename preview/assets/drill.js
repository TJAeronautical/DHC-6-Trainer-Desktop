const DRILL_PREVIEW = {
  index: 0,
  score: 0,
  answered: null,
  complete: false,
  missed: [],
  started: Date.now(),
  questions: [
    { q: "What is the first action following an engine fire warning in flight?", answers: ["Power lever — IDLE", "Fuel lever — OFF", "Generator — OFF", "Propeller — FEATHER"], correct: 0, explanation: "Reduce power on the affected engine first, then continue the approved memory sequence." },
    { q: "What condition best describes a hung start?", answers: ["No ITT rise", "Ng stabilizes below normal idle", "ITT exceeds limit", "Starter fails to engage"], correct: 1, explanation: "A hung start occurs when Ng stagnates below normal idle despite continued light-off." },
    { q: "What is the purpose of propeller feathering?", answers: ["Increase reverse thrust", "Reduce drag after engine failure", "Control cabin pressure", "Increase generator output"], correct: 1, explanation: "Feathering aligns the blades near the airflow to minimize drag from an inoperative engine." },
  ],
};

function renderDrill() {
  const shell = document.getElementById("mainShell");
  if (DRILL_PREVIEW.complete) {
    shell.replaceChildren(headerBar("Drill", "Session complete"), renderSessionResultPreview({
      title: "Knowledge Drill", mode: "Drill", correct: DRILL_PREVIEW.score,
      total: DRILL_PREVIEW.questions.length, elapsed: "00:36",
      focus: DRILL_PREVIEW.missed,
      onReview: () => { DRILL_PREVIEW.complete=false; DRILL_PREVIEW.index=0; DRILL_PREVIEW.score=0; DRILL_PREVIEW.answered=null; DRILL_PREVIEW.missed=[]; renderDrill(); },
      onRetry: () => { DRILL_PREVIEW.complete=false; DRILL_PREVIEW.index=0; DRILL_PREVIEW.score=0; DRILL_PREVIEW.answered=null; DRILL_PREVIEW.missed=[]; DRILL_PREVIEW.started=Date.now(); renderDrill(); },
      onHome: () => { location.hash="#/dashboard"; }
    }));
    return;
  }
  const item = DRILL_PREVIEW.questions[DRILL_PREVIEW.index];
  const elapsed = Math.floor((Date.now() - DRILL_PREVIEW.started) / 1000);
  const labels = ["A", "B", "C", "D"];
  const optionNodes = item.answers.map((answer, i) => {
    const answered = DRILL_PREVIEW.answered !== null;
    const classes = ["drill-option"];
    if (answered && i === item.correct) classes.push("correct");
    else if (answered && i === DRILL_PREVIEW.answered) classes.push("wrong");
    return el("button", {
      class: classes.join(" "),
      onclick: () => {
        if (DRILL_PREVIEW.answered !== null) return;
        DRILL_PREVIEW.answered = i;
        if (i === item.correct) DRILL_PREVIEW.score++; else DRILL_PREVIEW.missed.push(item.q);
        renderDrill();
      },
    }, [el("span", { class: "drill-letter" }, labels[i]), el("strong", {}, answer)]);
  });

  const feedback = DRILL_PREVIEW.answered === null ? null : el("div", {
    class: `drill-feedback ${DRILL_PREVIEW.answered === item.correct ? "good" : "bad"}`,
  }, [
    el("strong", {}, DRILL_PREVIEW.answered === item.correct ? "Correct!" : "Incorrect"),
    el("span", {}, DRILL_PREVIEW.answered === item.correct ? item.explanation : `Correct answer: ${item.answers[item.correct]}. ${item.explanation}`),
  ]);

  const next = DRILL_PREVIEW.answered === null ? null : el("div", { class: "drill-actions" }, [
    el("span", {}, `Score ${DRILL_PREVIEW.score} / ${DRILL_PREVIEW.index + 1}`),
    el("button", { class: "drill-next", onclick: () => {
      if (DRILL_PREVIEW.index < DRILL_PREVIEW.questions.length - 1) {
        DRILL_PREVIEW.index++;
        DRILL_PREVIEW.answered = null;
      } else {
        DRILL_PREVIEW.complete = true;
      }
      renderDrill();
    } }, DRILL_PREVIEW.index < DRILL_PREVIEW.questions.length - 1 ? "NEXT QUESTION" : "RESTART DRILL"),
  ]);

  shell.replaceChildren(
    headerBar("Drill", "Multiple choice A/B/C/D knowledge check"),
    el("div", { class: "drill-screen" }, [
      el("aside", { class: "drill-decks" }, [
        el("div", { class: "drill-deck-title" }, "DRILL DECKS"),
        ...["PT6A Engine", "Twin Otter Systems", "Abnormal Procedures", "Performance"].map((name, i) =>
          el("button", { class: `drill-deck ${i === 0 ? "active" : ""}` }, [el("strong", {}, name), el("small", {}, `${12 + i * 4} questions`)])),
      ]),
      el("section", { class: "drill-main" }, [
        el("div", { class: "drill-top" }, [
          el("div", {}, [el("small", {}, `QUESTION ${DRILL_PREVIEW.index + 1} OF ${DRILL_PREVIEW.questions.length}`), el("h2", {}, "Knowledge Drill")]),
          el("div", { class: "drill-stats" }, [el("span", {}, `TIME ${String(Math.floor(elapsed / 60)).padStart(2,"0")}:${String(elapsed % 60).padStart(2,"0")}`), el("span", {}, `SCORE ${DRILL_PREVIEW.score}`)]),
        ]),
        el("div", { class: "drill-progress" }, el("i", { style: `width:${((DRILL_PREVIEW.index + 1) / DRILL_PREVIEW.questions.length) * 100}%` })),
        el("div", { class: "drill-question" }, [el("small", {}, "PT6A ENGINE"), el("h3", {}, item.q)]),
        el("div", { class: "drill-grid" }, optionNodes),
        feedback,
        next,
      ]),
    ])
  );
}
