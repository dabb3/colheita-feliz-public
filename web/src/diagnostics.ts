export interface DiagnosticInfo { browser: string; version?: string }
interface Options extends DiagnosticInfo {
  base: string;
  token: () => Promise<string>;
  pending: boolean;
  send?: typeof fetch;
  now?: () => number;
  timeoutMs?: number;
}
type Status = 'ok' | 'network' | 'timeout' | 'invalid-response' | 'session-unavailable' | `http-${number}`;
interface Probe { status: Status; durationMs: number; serverTime?: string; originAllowed?: boolean }
export interface DiagnosticResult { health: Probe; auth: Probe; clockOffsetMs?: number; report: string }

// Only browser family/version; never copy the full user agent or device model.
export function diagnosticBrowser(agent: string): string {
  return /(?:EdgA|EdgiOS|Edg|Firefox|FxiOS|Chrome|CriOS|Version)\/[\d.]+/.exec(agent)?.[0] ?? 'Não identificado';
}

export async function runDiagnostics(options: Options): Promise<DiagnosticResult> {
  const now = options.now ?? Date.now, send = options.send ?? fetch;
  const startedAt = now();
  let authStarted = startedAt;
  async function probe(path: string, authenticated: boolean): Promise<Probe> {
    const started = now(), controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    try {
      const work = async (): Promise<Probe> => {
        let token: string | undefined;
        if (authenticated) {
          try { token = await options.token(); }
          catch { return {status:'session-unavailable',durationMs:now()-started}; }
          if (!token) return {status:'session-unavailable',durationMs:now()-started};
        }
        if (controller.signal.aborted) return {status:'timeout',durationMs:now()-started};
        const response = await send(options.base + path, {
          method:'GET', cache:'no-store', credentials:'omit', redirect:'error', signal:controller.signal,
          headers: token ? {Authorization:`Bearer ${token}`} : {},
        });
        if (!response.ok) return {status:`http-${response.status}`,durationMs:now()-started};
        let data;
        try { data = await response.json(); }
        catch { return {status:controller.signal.aborted?'timeout':'invalid-response',durationMs:now()-started}; }
        if (data?.ok !== true || (authenticated && (typeof data.serverTime !== 'string' || !Number.isFinite(Date.parse(data.serverTime)) || typeof data.originAllowed !== 'boolean')))
          return {status:'invalid-response',durationMs:now()-started};
        return {status:'ok',durationMs:now()-started,...(authenticated?{serverTime:new Date(data.serverTime).toISOString(),originAllowed:data.originAllowed}:{})};
      };
      return await Promise.race([work(), new Promise<Probe>(resolve=>{
        timer=setTimeout(()=>{controller.abort();resolve({status:'timeout',durationMs:now()-started});},options.timeoutMs??8000);
      })]);
    } catch {
      // A fetch rejection alone cannot distinguish DNS, TLS, CORS and browser blocking.
      return {status:controller.signal.aborted?'timeout':'network',durationMs:now()-started};
    } finally { clearTimeout(timer); }
  }
  const health = await probe('/health',false);
  authStarted = now();
  const auth = await probe('/v1/diagnostics',true);
  const clockOffsetMs = auth.serverTime ? Math.round(Date.parse(auth.serverTime)-(authStarted+auth.durationMs/2)) : undefined;
  const version=/^[a-f0-9]{7,40}$/.test(options.version??'')?options.version:'não identificada';
  const browser=diagnosticBrowser(options.browser);
  const report=[
    'Colheita Feliz — diagnóstico de conexão',
    `Horário do navegador: ${new Date(startedAt).toISOString()}`,
    `Versão web: ${version}`, `Navegador: ${browser}`,
    `API pública: ${health.status} (${Math.round(health.durationMs)} ms)`,
    `Consulta autenticada: ${auth.status} (${Math.round(auth.durationMs)} ms)`,
    `Comunicação/origem: ${auth.status==='ok'?(auth.originAllowed?'confirmada':'sem origem informada'):'não confirmada'}`,
    `Diferença estimada de relógio (servidor menos navegador): ${clockOffsetMs===undefined?'indisponível':`${clockOffsetMs} ms; incerteza de rede ±${Math.ceil(auth.durationMs/2)} ms`}`,
    `Ação pendente: ${options.pending?'preservada, sem reenvio':'nenhuma no início do teste'}`,
    'Somente leitura. Não verifica banco nem executa ações. Falha de rede não identifica sozinha DNS, TLS, CORS ou bloqueio.',
  ].join('\n');
  return {health,auth,clockOffsetMs,report};
}
