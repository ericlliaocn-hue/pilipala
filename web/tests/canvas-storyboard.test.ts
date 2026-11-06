import { expect, test } from "bun:test";

import { buildStoryboardPrompt } from "../src/lib/canvas/canvas-storyboard";

test("builds a 3x3 storyboard prompt with the trimmed script", () => {
    const prompt = buildStoryboardPrompt("  女孩站在雨中的街头  ", 3);

    expect(prompt).toContain("3×3");
    expect(prompt).toContain("共 9 格");
    expect(prompt).toContain("女孩站在雨中的街头");
    expect(prompt).not.toContain("  女孩站在雨中的街头");
});

test("builds a 5x5 storyboard prompt", () => {
    const prompt = buildStoryboardPrompt("夜里的城市追逐戏", 5);

    expect(prompt).toContain("5×5");
    expect(prompt).toContain("共 25 格");
});
