export function buildStoryboardPrompt(script: string, size: 3 | 5): string {
    const total = size * size;
    return `生成一张 ${size}×${size} 的电影分镜宫格图，共 ${total} 格，所有格子等宽等高、紧密排列成正方形网格，格子之间不要留白边、不要分隔线、不要编号和文字。
从左到右、从上到下依次表现下面剧情中的连续镜头，景别和机位要有变化（远景、中景、近景、特写交替），同一角色在所有格子中的外貌、服装和画风保持一致。

剧情：
${script.trim()}`;
}
