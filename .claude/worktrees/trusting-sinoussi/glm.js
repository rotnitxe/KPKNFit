import OpenAI from 'openai';
import readline from 'readline';

const client = new OpenAI({
  apiKey: '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',
  baseURL: 'https://integrate.api.nvidia.com/v1',
});

const history = [];

async function ask(prompt) {
  history.push({ role: 'user', content: prompt });

  const stream = await client.chat.completions.create({
    model: 'z-ai/glm5',
    messages: history,
    temperature: 1,
    top_p: 1,
    max_tokens: 4096,
    stream: true,
    // @ts-ignore
    extra_body: { chat_template_kwargs: { enable_thinking: true, clear_thinking: true } },
  });

  let fullResponse = '';
  process.stdout.write('\nGLM5: ');
  for await (const chunk of stream) {
    const delta = chunk.choices[0]?.delta;
    if (!delta) continue;
    const text = delta.content || '';
    process.stdout.write(text);
    fullResponse += text;
  }
  console.log('\n');

  history.push({ role: 'assistant', content: fullResponse });
}

// One-shot mode: node glm.js "your question here"
if (process.argv[2]) {
  await ask(process.argv.slice(2).join(' '));
  process.exit(0);
}

// Interactive mode
const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
console.log('GLM5 Chat — escribe tu pregunta (o "salir" para terminar)\n');

function prompt() {
  rl.question('Tú: ', async (input) => {
    input = input.trim();
    if (!input) return prompt();
    if (input.toLowerCase() === 'salir' || input.toLowerCase() === 'exit') {
      console.log('¡Hasta luego!');
      rl.close();
      return;
    }
    await ask(input);
    prompt();
  });
}

prompt();
