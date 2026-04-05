import OpenAI from 'openai';

const openai = new OpenAI({
  apiKey: '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',
  baseURL: 'https://integrate.api.nvidia.com/v1',
});

async function main() {
  const completion = await openai.chat.completions.create({
    model: "z-ai/glm5",
    messages: [{ "role": "user", "content": "Say hello in one sentence." }],
    temperature: 1,
    top_p: 1,
    max_tokens: 1024,
    stream: true,
    // @ts-ignore
    extra_body: { chat_template_kwargs: { enable_thinking: true, clear_thinking: false } },
  });

  for await (const chunk of completion) {
    const delta = chunk.choices[0]?.delta;
    if (!delta) continue;

    const reasoning = delta.reasoning_content;
    if (reasoning) process.stdout.write(reasoning);

    const content = delta.content;
    if (content) process.stdout.write(content);
  }

  console.log(); // newline at end
}

main().catch(console.error);
