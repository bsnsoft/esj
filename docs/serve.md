# esj serve and esj mcp

*Part of [EN16931 Semantic JSON](../README.md).*

**Preview:** `esj serve`, `esj mcp`, the REST API, the MCP tools and their JSON may change in any
minor release ([`compatibility.md`](compatibility.md)). The body of `POST /api/validate` is not:
it is the report of `esj validate --output json` over the same bytes, byte for byte.

`esj serve` offers the tools of the command line over HTTP: a REST API under `/api`, its OpenAPI
3.1 description at `/openapi.json`, and an MCP server (Streamable HTTP) at `/mcp`. `esj mcp` is
the same tools as an MCP server over the standard streams. Neither reads a document itself:
every call runs `esj` as a child process with `--job-heap` and `--job-timeout`, so a document
that exhausts either costs that call and not the server ([`deployment.md`](deployment.md)).

```text
esj serve                                              # http://127.0.0.1:8080
docker run --rm -p 8080:8080 ghcr.io/bsnsoft/esj serve # listens on 0.0.0.0 in the container
docker compose -f dist/compose.yaml up esj-api         # read-only, tmpfs, memory limit
```

## Tools

| Tool | REST | Parameters | Result |
|---|---|---|---|
| `validate` | `POST /api/validate` | `report` none\|pdf\|html, `lang` de\|en, `extension` | verdict, reasons, the 20 heaviest findings with rule, paths, severity, message; the report as a file; over MCP and HTTP the complete JSON report as a file where the result had to be cut |
| `summary` | `POST /api/summary` | `extension` | number, dates, type, currency, seller, buyer, totals, lines — read, not validated |
| `get` | `POST /api/get` | `paths` (1–64; a group path, or an asterisk for any index), `extension` | the values below the paths in document order, at most 500, and the paths without one |
| `convert` | `POST /api/convert` | `to` esj\|ubl\|cii, `fail_on_loss`, `ubl_document`, `extension` | what was written, what had no place; the document inline up to 64 KiB, and as a file |
| `render` | `POST /api/render` | `format` pdf\|html, `layout` letter\|generic, `embed` none\|cii, `lang`, `template`, `extension` | the rendering as a file |
| `extract` | `POST /api/extract` | `list`, `attachment` | the invoice of a PDF, inline up to 64 KiB and as a file; or the attachments |
| `inspect` | `POST /api/inspect` | `extension` | the page of `esj inspect` |
| `upload` | `POST /api/documents` | `content_base64`, `name` (MCP) | `id`, `sha256`, `bytes`, `expires` |

A tool reads the document as a file: over REST the request body, `document=<id>` of an upload or
an artefact, or `path=` below an `--allow-dir`; over MCP and HTTP the `document` id; over the
standard streams a `path`, and the file a tool writes goes to the `out` the call names. A file a
call writes over HTTP is an artefact, `GET /api/artifacts/<id>`, kept for `--ttl`, and a rendered
hybrid PDF is validated by its id. `GET /` names the endpoints. A value is returned exactly as
the document states it; one longer than 32 Ki characters is named with its length under
`omitted` and not carried (`convert to=esj` writes the whole document).

## REST

```console
$ curl -s --data-binary @conformance/pdf/factur-x.pdf http://127.0.0.1:8080/api/validate
{
  "input": "<stdin>",
  "detected": "cii",
...
  "verdict": "VALID"
}
$ curl -s --data-binary @examples/standard-invoice.esj.json \
       'http://127.0.0.1:8080/api/get?paths=/BT-1&paths=/BG-25/*/BT-131'
{
  "values": {
    "/BT-1": "RE-2026-0042",
    "/BG-25/0/BT-131": "250",
    "/BG-25/1/BT-131": "1440",
    "/BG-25/2/BT-131": "760"
  },
  "missing": [],
  "truncated": false
}
$ curl -s --data-binary @examples/standard-invoice.esj.json \
       'http://127.0.0.1:8080/api/render?embed=cii'
{
  "format": "pdf",
  "embedded": true,
  "files": [
    {
      "role": "rendering",
      "name": "invoice.pdf",
      "mediaType": "application/pdf",
...
```

The parameters are in the query; a JSON object body that names only parameters of the operation
is taken as them, which is how OpenAPI tool clients call, and any other body is
the document. `report=pdf` leaves the body of `validate` unchanged and names the report in a
`Link` header. An upload is kept for `--ttl` and used by its id:

```text
curl -s --data-binary @invoice.pdf http://127.0.0.1:8080/api/documents      # {"id": "<id>", …}
curl -s -X POST 'http://127.0.0.1:8080/api/validate?document=<id>&report=pdf'
curl -s -o report.pdf http://127.0.0.1:8080/api/artifacts/<id of the report>
```

| Child exit code | Meaning | HTTP | MCP |
|---|---|---|---|
| 0, 1, 9 | `validate`, `inspect`: VALID, INVALID, INDETERMINATE; others: done | 200 | result |
| 8 | `convert` with `fail_on_loss` wrote nothing | 200 | result |
| 2 | the document could not be read, recognized or parsed | 422 | `isError` |
| 4 | a feature this version does not implement | 422 | `isError` |
| 3, 7, killed at `--job-timeout` or past 64 MiB written | heap, bound, deadline or output of the child: no verdict | 507 | `isError` |
| 5, 6 | an internal error | 500 | `isError` |
| any other | the child crashed | 502 | `isError` |

The server itself answers 400 for parameters that do not fit, 401 without the token, 403 for a
foreign `Origin` or a path outside `--allow-dir`, 404, 405, 413 for a body past `--max-upload`
(refused on its `Content-Length` before it is read, cut while it is read otherwise), and 503
with `Retry-After` where every child is busy and `--max-queue` calls wait, the store or the
disk (`--max-disk`) is full, or the server is shutting down.

## MCP

The revisions with an `initialize` handshake, 2024-11-05 to 2025-11-25; a client that names another
is answered with 2025-11-25. No session: no `Mcp-Session-Id`, `GET` and `DELETE` are 405, a
notification is 202. A result is a short text, the same data as JSON text, `structuredContent`
and a `resource_link` per file with an absolute URL from 2025-06-18 on; a refused call is
`isError` with the reason. A batch, which only 2025-03-26 has, is answered once its last
message is: it carries at most 16 messages and one `tools/call`, and a larger one is refused
with -32600. `notifications/cancelled` ends the child of the call it names: over
the standard streams the call is then not answered; over HTTP, without a session, it names the
call of that id from the same address with the same `Authorization`, and the call is answered
with the error -32800. A client that drops its HTTP connection does not end its call — the
JDK's server does not tell — so its child runs to its end or to `--job-timeout`.

```text
claude mcp add esj -- esj mcp                                               # Claude Code, stdio
claude mcp add --transport http esj http://127.0.0.1:8080/mcp \
    --header "Authorization: Bearer $(cat esj-token)"                       # Claude Code, HTTP
```

```json
{"mcpServers": {"esj": {"command": "/opt/homebrew/bin/esj", "args": ["mcp"]}}}
```

The second block is Claude Desktop's `claude_desktop_config.json` and Claude Code's `.mcp.json`;
both start `esj mcp` and need its absolute path. Claude Desktop reaches a remote MCP server only
as a custom connector, from Anthropic's servers, so `esj serve` would have to be public.

**Open WebUI** (read in 0.11.4) hands an external tool server only the arguments a model writes;
a file attached to a chat reaches only Open WebUI's own Python tools. Such a tool sends the
file's bytes to `POST /api/validate`; esj ships none.

## Security

- **Process boundary.** One child per call: `--job-heap` (`ESJ_MAX_HEAP`, else 512m),
  `--job-timeout` (5m) as its `--max-runtime` and a kill 5 s later, `--max-jobs` (half the
  processors) at once, `--max-queue` waiting for `--queue-wait`, `--limits` as for every command.
- **Heap of the server.** The server holds no document and no answer whole: it reads what a
  child wrote from its file, token by token, and keeps what a result carries — about 128 Ki
  characters, the 20 heaviest findings, a value of at most 32 Ki characters. All of that, and
  the answer written from it, exists only while the call holds the place of a child: the answer
  goes into a spool, 16 KiB in the heap and the rest in a file, before the place is given back,
  and is sent from there however slowly the client reads; the body of `POST /api/validate` and
  every artefact are sent from their files. A message of a client is held to 64 Ki characters
  and 1 024 values beside the content of an upload, which goes to a file. Measured with
  `--limits large` (the smallest heap that survives the calls, beside a ballast): a server
  that answers small calls needs 7–8 MiB, a running call adds at most 3 MiB — the largest
  result, three values of 32 Ki characters that every text escapes, 2.5 MB of answer; a million
  values summarised, fifty thousand findings cut, and the rest of the tools add less — and a
  thread with a message at both bounds about 0.2 MiB. On start the server needs at least twice
  that, `32 MiB + max-jobs × 6 MiB + (max-queue + 16) × 1 MiB`, and lowers `--max-queue` and
  `--max-jobs`, with a warning, where its heap is smaller. The container image gives the server
  `ESJ_SERVE_HEAP` (256m) and every child `ESJ_MAX_HEAP`.
- **Network.** `--bind 127.0.0.1` by default, `0.0.0.0` in both container images
  (`ESJ_SERVE_BIND`); on any other address without a token the server warns on start. TLS is a
  reverse proxy's.
- **Token.** `--token-file` or `ESJ_TOKEN_FILE`, never an argument; compared in constant time; it
  guards `/api` and `/mcp`. `/` and `/openapi.json` are public: they say what a release says,
  and a client is configured from them before it holds the token.
- **Origin.** A browser origin that is neither loopback nor an `--allow-origin` is refused with
  403 (DNS rebinding); only an `--allow-origin` gets CORS headers.
- **Links.** A link to an artefact or an endpoint is absolute where the server knows its own
  address: `--public-url`, or a `Host` it answers to — a loopback name or the bind address with
  its port, the host of an `--allow-origin`, an `--allow-host` such as `esj-api:8080`. Any other
  `Host` gives the path, `/api/artifacts/<id>`, which a client resolves against the URL it
  used; an MCP result then names the path in its text. Behind a proxy or in Compose, name the
  address with `--public-url` or `--allow-host`.
- **Requests.** `--request-timeout` (60s) for headers and body, at most 64 header fields and
  32 KiB of them, idle connections closed after 30 s, `--max-upload` (32M). An answer a client
  does not read holds a thread, `--max-jobs` + `--max-queue` + 16 of them, until
  `--queue-wait` + `--job-timeout` + 125 s after its request was read, the response time of
  the JDK's server, which has no shorter write timeout; it holds no more heap than a spool.
  A request refused before its body was read — 401, 403, 503, a message that is not JSON — is
  answered at once, and the rest of its body is then read and dropped, up to its
  `Content-Length` and for at most 5 s, before the connection is closed, so that the client
  reads the answer and not a TCP reset. A body announced past the bound of its door
  (`--max-upload`, for `/mcp` its base64 and the message around it) is not read: 413, and the
  connection is closed, which a client that is still sending may see as a reset.
- **Disk.** `--max-disk` (448M) bounds what the temporary directory holds at once: request
  bodies and uploads as they are read, copies of files named by `path`, artefacts, answers that
  wait for their client, and 64 MiB for each running child from its start — the most it may
  write, its output and its files together. Every byte the server writes is counted before it
  is written; a child's standard output passes through the server and stops at its 64 MiB, its
  files are measured every 100 ms, and once it has ended what it wrote is counted in place of
  the 64 MiB. What does not fit is refused, never cut: 503 with `Retry-After: 30` — a body by
  its `Content-Length` before it is read, a call before its child starts, an answer as it is
  written; over MCP a result with `isError`. The server starts with no more `--max-jobs` than
  `--max-disk` holds beside the files of one call (`--max-upload` and its base64), lowered with
  a warning, and names its heap and its disk on start, and a file system with less room than
  `--max-disk`. The default leaves one child's 64 MiB to spare in the 512 MiB tmpfs of
  `dist/compose.yaml`, which counts against the memory limit of the container.
- **Files.** `path` only below an `--allow-dir`, by its real path, links followed and refused
  where they lead out; a file with a second name, a hard link, is refused; the file is copied
  before the child reads it. An allowed directory must not be writable by anybody the server
  does not trust: whoever writes there can link to any file the server can read. Templates by
  name from `--templates`, packs from `--packs` and `ESJ_PACKS`. No URL is ever fetched.
- **Data, not instructions.** A value comes back exactly as the document states it, as data.
  The text of an MCP result and the message of a protocol error write every control,
  bidirectional, zero-width or other invisible character as its `\uXXXX` escape, and so does
  every JSON the server writes itself; the body of `POST /api/validate` is the command line's
  report as it is.
- **Nothing kept.** Uploads, artefacts and the children's working directories live in one
  temporary directory of the process: `--ttl` (15m), `--max-stored` (256), `--max-stored-bytes`
  (256M) within `--max-disk`, removed on `SIGTERM`. A server that is killed rather than stopped
  leaves it behind, and no server removes another's; in the container it is a tmpfs. A child
  gets `PATH`, `HOME`, `LANG`, `LC_ALL`, `LC_CTYPE`, `TZ`, `TMPDIR` and `ESJ_PACKS` and no other
  variable. The log has one line per request — moment, method, path without query, status,
  bytes, duration, exit code, the first 12 digits of the SHA-256 — and no content of a document.
