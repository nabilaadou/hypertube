# Build the Hypertube cinematic landing page

Use this document as the implementation prompt and task checklist for the Hypertube frontend. Work through one task at a time. Start by reading the current repository and asking the user focused questions about the active task. Begin implementation when the user explicitly says that you may code.

## Follow the agreed workflow

1. Read `AGENTS.md` and the latest instructions in the conversation.
2. Select one unchecked task from this document.
3. Explain the intended visible result and ask about any unresolved choices for that task.
4. Wait for coding permission before changing application code. Permission to write this plan covers the Markdown document.
5. Implement the authorized task and verify its acceptance criteria.
6. Present a working preview and explain any remaining limitations.
7. Ask the user to review the result before starting the next task.
8. Mark a task complete only after its acceptance criteria are met. Record user approval separately from technical completion.

Use feature names for branches, such as `cinema-reveal` or `movie-timeline`. Never use the `codex/` prefix. Create a branch and a pull request for every change, link an existing issue when relevant, and leave the pull request open for human review and manual merging. Keep changes to `main` behind pull requests. Preserve unrelated local changes.

## Preserve the agreed creative direction

Build a movie-focused Hypertube landing page with the layout, visual rhythm, and transition pacing of the selected opening sections of JJettas. The page begins with one fullscreen movie edit, reveals a cinema around that same video, holds the cinema long enough to appreciate it, then introduces an awards timeline through a poster on the left. End this scope with a large statement about Hypertube.

Use the reference through the quote section shown in the user's screenshot, beginning with “I want people to remember me for more than football.” The user will define the later sections separately. Replace the sports content with cinema content and Hypertube branding.

Keep the reference's dark purple, near-black, warm off-white, and textured visual character. Give the theater the agreed black and antique-gold finish. Use oversized condensed display typography, restrained navigation, strong poster artwork, and deliberate spacing.

The current concept contains one edit and a cinema. The earlier avatar, red-and-blue glasses, two simultaneous edits, and visible projector concepts have been superseded. The user provided a theater model and wants that theater to inform the result.

Inspect the live reference before describing an animation as an exact match. Treat the current prototype's timing as a starting point that still needs visual comparison and user review. [JJettas reference](https://jjettas.com/)

## Use the requested frontend stack

- Use the existing Next.js application in `client/`, React, and TypeScript. Inspect the installed versions and repository conventions before changing dependencies.
- Use Tailwind CSS for layout, responsive styling, typography, and shared visual tokens.
- Use GSAP as the main animation controller. Use ScrollTrigger for pinned sections, scroll progress, and timeline movement where appropriate.
- Use Anime.js only when a specific effect benefits from it, such as an isolated text or decorative animation. Give each animated property one owner so GSAP and Anime.js cannot overwrite each other.
- Use Three.js when the approved theater experience requires real-time geometry, lighting, perspective, or camera movement. Decide whether a React integration is useful after inspecting the client. Adding an integration library is an implementation choice to explain.
- Keep browser-dependent animation and rendering inside suitable client components. Keep static content and data outside the animation loop where possible.

The existing client already declares Next.js, React, and TypeScript. The inspected package manifest does not yet declare Tailwind CSS, GSAP, Anime.js, or Three.js. Check the current manifest again when implementation starts.

## Use the existing prototype and assets

Read `design.html` in the repository root before implementation. It contains the current desktop prototype, including the timed opening reveal and delayed cinema exit. Translate its behavior into maintainable React components and scoped animation logic.

The current local preview uses these files.

- `design-assets/reel.mp4` contains the user's movie edit.
- `design-assets/reel-poster.jpg` supplies the initial video still.
- `design-assets/theater.png` supplies the rendered theater image.

The current theater preview uses an image rendered from a 3D scene. A real-time Three.js theater requires a separate implementation and review. Locate the provided model and the previously textured scene before creating new materials or geometry. Earlier source folders included `Private_home_cinema_-_Baking`; paths may have changed as assets were reorganized.

The original theater's baked textures were unavailable during the earlier work. Inspect the actual model and materials before promising that its source appearance can be reproduced exactly. Preserve the agreed black and antique-gold treatment.

Keep large videos, source models, texture packages, and generated media outside Git commits. The repository currently ignores `assets/` and `/design-assets/`. Check that each asset is untracked and ignored before committing. An ignore rule does not remove a file already tracked by Git.

Choose a local asset-serving path compatible with Next.js during the asset task. Repository-root assets require an explicit serving arrangement. For deployment, agree on a media-hosting destination before uploading the user's files. Store configurable asset references in code and explain how another developer supplies the local files.

## Consult the requested asset and inspiration sources

Use these sources to find effects, components, and assets that support the approved design. Inspect each candidate's actual implementation, license, dependencies, and browser support. Record the source of anything adopted. Bring only the selected assets or components into the project.

- Consult Prompt Motion for motion prompts and animation studies that help describe timing, typography, and transitions. [Prompt Motion](https://prompt-motion.com/)
- Inspect Originkit for animated components that fit the navigation and supporting controls. Its homepage was reachable during planning, but its component details were not readable through the text browser. Verify specific components before selecting them. [Originkit](https://www.originkit.dev/)
- Inspect Shaders for atmospheric effects and shader components. Its homepage advertises WebGPU components, so evaluate compatibility and a fallback before adopting an effect. [Shaders](https://shaders.com/)
- Inspect Lightswind for React components and UI blocks that fit the design. Check whether a candidate adds another animation dependency. [Lightswind](https://lightswind.com/)
- Inspect 21st for React components, navigation, cards, backgrounds, and hero treatments. Adapt selected components to Hypertube's palette and interaction model. [21st](https://21st.dev/)
- Consult GetLayers for cinematic website prompts and 3D scene inspiration. Confirm what a selected resource actually provides and whether access is available. [GetLayers](https://www.getlayers.ai/)

## Task 1 — Inspect the project and confirm the first implementation

### Steps

1. Read the existing client structure, package scripts, styles, and routing.
2. Open `design.html` with its local media and review the current sequence.
3. Inspect JJettas on desktop through the agreed stopping point.
4. Record the opening trigger, reveal duration, easing, cinema hold, poster position, and timeline entry as observed behavior.
5. Locate the user's edit, theater model, rendered theater, and draft posters.
6. Ask whether the first implementation should use the approved theater render or a real-time Three.js scene.
7. Confirm what an upward scroll at the beginning of the cinema should do, including whether it replays the fullscreen opening.

### Acceptance criteria

- [ ] The active files and assets are located.
- [ ] The section order and interaction behavior are written down.
- [ ] The theater rendering approach is agreed with the user.
- [ ] Unresolved behavior is explicitly identified.
- [ ] The user has authorized the next implementation task.

## Task 2 — Prepare styles, dependencies, and media references

### Steps

1. Configure Tailwind CSS using an approach compatible with the existing client.
2. Add GSAP and the React integration needed by the chosen implementation.
3. Add Anime.js or Three.js when an approved task requires them.
4. Define shared colors, typography, spacing, and responsive values using the prototype as the visual starting point.
5. Create a small movie-data representation containing title, poster, year, award label, and trailer reference.
6. Establish local media URLs and a visible fallback for missing files.
7. Confirm that large assets are ignored and excluded from staged changes.

### Acceptance criteria

- [ ] The existing Next.js client runs with the selected styling setup.
- [ ] Shared styles reflect the agreed palette.
- [ ] The user's video and theater media load through the selected local setup.
- [ ] Missing media produces a usable fallback.
- [ ] The proposed commit contains only intentional source and configuration changes.

## Task 3 — Build the fullscreen opening edit

### Steps

1. Display the user's single movie edit across the initial viewport.
2. Choose the video crop to preserve the important content at desktop sizes.
3. Display the poster image while the video loads.
4. Add Hypertube branding, the menu control, the scroll cue, and an accessible sound control where appropriate.
5. Start muted playback when the browser permits it and provide a usable play action when autoplay is blocked.
6. Keep the video element available for the transition into the theater screen.

### Acceptance criteria

- [ ] The opening shows the user's edit.
- [ ] Loading avoids an unexplained blank screen.
- [ ] The controls are readable over the footage and work with a keyboard.
- [ ] The composition is approved before the reveal animation begins implementation.

## Task 4 — Make the video transition automatically into the screen

The first downward scroll triggers a timed reveal that completes on its own. During that reveal, further scroll input cannot advance the page into the movie timeline.

### Steps

1. Represent the opening, active reveal, and settled cinema as explicit interaction states.
2. Trigger the reveal from the first intentional downward scroll or the visible scroll action.
3. Animate the fullscreen video into the theater screen while the theater becomes visible around it.
4. Keep playback continuous so the edit retains its current frame and playback position.
5. Consume wheel, touch, and relevant keyboard navigation input during the reveal without breaking ordinary control activation.
6. Handle trackpad momentum so the gesture that started the reveal cannot immediately skip the cinema hold.
7. Release scroll control when the animation completes and the input gesture has settled.
8. Align the video's final corners with the theater screen at supported viewport sizes.

### Starting values to review

The current prototype uses an approximately 850 ms reveal and a brief 180 ms input-quiet interval. Treat these values as adjustable prototype settings. Compare the visual result with JJettas and tune it with the user.

### Acceptance criteria

- [ ] One downward gesture starts the complete reveal.
- [ ] Continued scrolling cannot scrub or skip the active reveal.
- [ ] The video lands precisely in the theater screen and continues playing.
- [ ] A strong trackpad gesture leaves the cinema visible after the reveal.
- [ ] Scroll control releases reliably, including after interruption or navigation.

## Task 5 — Finish the cinema composition and hold

### Steps

1. Present the provided theater with the agreed black and antique-gold finish.
2. If real-time 3D is approved, import and inspect the model, then tune materials, lighting, screen placement, and camera framing.
3. If real-time 3D is used, connect the playing video to the screen without restarting playback during the reveal.
4. Position the featured movie poster on the left without covering essential screen content.
5. Keep the theater composition stable after the opening finishes.
6. Reserve a separate stretch of scrolling before the cinema exit begins.
7. Start with roughly one viewport of scroll travel for the hold, based on the current prototype, and adjust after review.

### Acceptance criteria

- [ ] The theater has time to remain fully visible before the timeline appears.
- [ ] The video stays aligned with the screen throughout the hold.
- [ ] The featured poster stays visible on the left.
- [ ] The first scroll gesture cannot consume the cinema hold accidentally.
- [ ] The user approves the hold duration and theater framing.

## Task 6 — Carry the left poster into the movie timeline

### Steps

1. Begin the cinema exit only after the hold distance has been completed.
2. Use the approved diagonal wipe from the current prototype as the starting composition.
3. Move and scale the featured poster from its left-side position into the first timeline position.
4. Preserve the poster's visual continuity during the handoff.
5. Reveal the timeline background and labels in the intended order.
6. Support reverse scrolling through the transition without duplicate posters or sudden jumps.

### Acceptance criteria

- [ ] The wipe starts after the cinema hold.
- [ ] The featured poster visibly becomes part of the timeline.
- [ ] The cinema and timeline have deliberate overlap during the transition.
- [ ] Scrolling in either direction preserves a continuous composition.
- [ ] Resizing does not displace the poster from its intended destination.

## Task 7 — Build the awards timeline and trailer action

### Steps

1. Use the draft posters while the user prepares the final collection.
2. Ask which award determines the winner and whether labels use release years or award ceremony years.
3. Confirm the year range and whether the timeline moves forward or backward through time.
4. Arrange one selected winning movie per year in the approved range.
5. Connect scroll progress to poster movement, year labels, and the active movie.
6. Keep poster dimensions and typography consistent with the reference composition.
7. Open the selected movie's trailer when its poster is activated.
8. Provide close controls, Escape handling, focus restoration, and clear playback failure feedback.

The existing draft labels include Oppenheimer with 2024, Everything Everywhere All at Once with 2023, CODA with 2022, and Parasite with 2020. These are prototype entries. Confirm their intended award-year meaning and the final collection before presenting the timeline as complete.

### Acceptance criteria

- [ ] Each poster corresponds to the correct title, chosen year convention, and trailer.
- [ ] The timeline progresses at a readable pace.
- [ ] Poster activation works with both pointer and keyboard input.
- [ ] Closing a trailer returns the visitor to the same place in the landing page.
- [ ] Draft content is documented for the user to replace.

## Task 8 — End with the Hypertube statement

### Steps

1. Request or propose the final Hypertube statement for user approval.
2. Use the reference quote section's oversized condensed type, centered layout, and textured purple background.
3. Retain the prototype's dotted text treatment if approved after comparison with the reference.
4. Give the text reveal its own readable timing after the timeline ends.
5. Set the end of the current landing-page scope at this statement section.

### Acceptance criteria

- [ ] The content describes Hypertube and has user approval.
- [ ] The statement remains readable during and after its animation.
- [ ] The end of the timeline leads naturally into the statement.
- [ ] This section matches the agreed stopping point.

## Task 9 — Review responsiveness, accessibility, and animation behavior

Complete the desktop composition first. Ask the user to review the mobile adaptation before committing to a different camera crop or timeline layout.

### Steps

1. Inspect desktop layouts at several viewport sizes and browser zoom levels.
2. Recalculate animation measurements when viewport or content dimensions change.
3. Provide reduced-motion behavior that keeps all content and controls reachable.
4. Check keyboard navigation, visible focus, trailer focus handling, and sound controls.
5. Pause unnecessary media or rendering work when the page is hidden or the relevant section is inactive.
6. Clean up event listeners, GSAP contexts, ScrollTriggers, video resources, and Three.js resources when their components unmount.
7. Check repeated route entry and exit for duplicate animation handlers and stuck scroll locks.
8. Review mobile composition and gestures with the user, then implement the approved adaptation.

### Acceptance criteria

- [ ] Desktop resizing preserves the screen alignment and poster transitions.
- [ ] Reduced-motion users can reach every section.
- [ ] The page remains usable when media loading fails or autoplay is blocked.
- [ ] Navigation and repeated visits leave scroll behavior functional.
- [ ] The approved mobile version keeps the edit, cinema, posters, and statement readable.

## Task 10 — Deliver the frontend for review

### Steps

1. Run the repository's relevant build and lint checks.
2. Review the complete user journey in a browser with the real local media.
3. Compare the sequence with the approved reference scope and record remaining visual differences.
4. Inspect the staged diff and file sizes before committing.
5. Keep source media and large generated assets out of the push.
6. Document how to run the frontend and supply its local assets.
7. Push the feature branch and open a pull request for human review.
8. Include a preview, verification results, asset requirements, and known limitations in the handoff.

### Acceptance criteria

- [ ] The full sequence works from opening edit through the Hypertube statement.
- [ ] The user can run and review the result from the documented instructions.
- [ ] The pull request contains the intended implementation and no large media files.
- [ ] The pull request remains open for a human to review and merge.

## Start the next working session

Read this document and the current repository. Begin with Task 1. Ask focused questions about the theater rendering approach and unresolved scroll behavior. Present the findings, then wait for explicit coding permission. Complete only the authorized task and present its result before advancing.
