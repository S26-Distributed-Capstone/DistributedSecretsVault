## Deliverable Milestone Plan

**Distributed Secrets Vault**

Each week requires:

- a **Running Artifact** (deployable and demonstrable), and
- a **Scope / Design Artifact** describing what actually runs.

You will be graded weekly against these milestones.

---

## Milestones

| Week   | Focus                                      | Running Artifact                                                                                                                                                | Scope / Design Artifact                                                      |
| ------ | ------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| **1**  | Project Bootstrap & Scope Definition       | • Secrets service starts and responds to basic API endpoints (`/get`, `/post`, etc) <br>• Single-node deployment reproducible                                   | • Initial scope document (1–2 pages)<br>• Explicit assumptions and non-goals |
| **2**  | Architecture & API Contract                | • Create, update, and retrieve endpoints callable (stubbed allowed)                                                                             | • API contract frozen<br>• Initial architecture diagram                      |
| **3**  | Secret Existence & Duplicate Semantics     | • Secret creation succeeds and shards distributed to nodes<br>• Each node stores only one shard (plaintext never on disk)<br>• Duplicate create requests fail   | • Secret existence and duplicate handling rules                              |
| **4**  | Single-Node Update & Versioning            | • Updates create new versions<br>• History preserved with validity intervals                                                                                    | • Validity guarantees                                                        |
| **5**  | Shamir Shard Storage Record Format         | • Secret shards stored durably on each node<br>• Records include shard data, metadata (user:key:version)<br>                                                    | • Shard storage record definition                                            |
| **6**  | Idempotency & Retry Semantics              | • Safe retries demonstrated for create and update                                                                                                               | • Idempotency rules and limitations                                          |
| **7**  | Multi-Node Shard Distribution Path         | • Shards distributed to n nodes (configurable)<br>• Each node stores one shard per secret version<br>• k-of-n threshold reconstruction tested                   | • Shard distribution model and failure assumptions                           |
| **8**  | Multi-Node Consistency                     | • Consistent behavior with all nodes healthy<br>• Reads reconstruct secrets by gathering k shards<br>• Plaintext only exists in memory during reconstruction    | • Updated scope reflecting implemented behavior                              |
| **9**  | Failure Handling                           | • One vault node killed during operation<br>• System remains usable (up to n-k node failures)<br>• Secret reconstruction still possible with k available shards | • Failure behavior and degraded modes                                        |
| **10** | Concurrency and History Safety             | • Concurrent create/update tested<br>• Duplicate and update rules enforced correctly                                                                            | • Concurrency guarantees                                                     |
| **11** | Multi-Secret `.env` Creation & Resolution  | • `.env encrypt` creates secrets correctly<br>• `.env expand` resolves secrets correctly<br>• Duplicate secrets cause full failure                              | • `.env` transformation and failure semantics                                |
| **12** | History Retrieval & Observability          | • Secret history endpoint returns ordered versions with validity ranges<br>• Logs show create, update, retrieve, and failure                                    | • History query semantics and observability notes                            |
| **13** | Final Demo & Submission                    | • Live multi-node demo showing create/update, concurrency, failure, recovery, history access, and `.env` workflows                                              | • Final scope document<br>• Clear statement of guarantees and limitations    |

---

## Notes

- Running systems matter: every claim must be observable.
- Documentation must describe what actually runs, not intended design.
- Correctness and clarity are the main goal.
