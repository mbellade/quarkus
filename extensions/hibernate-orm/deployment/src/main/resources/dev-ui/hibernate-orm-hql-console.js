import {css, html, QwcHotReloadElement} from 'qwc-hot-reload-element';
import {RouterController} from 'router-controller';
import {JsonRpc} from 'jsonrpc';
import '@vaadin/icon';
import '@vaadin/button';
import '@vaadin/combo-box';
import '@vaadin/text-field';
import '@vaadin/text-area';
import '@vaadin/progress-bar';
import '@vaadin/tabs';
import '@vaadin/tabsheet';
import {notifier} from 'notifier';

export class HibernateOrmHqlConsoleComponent extends QwcHotReloadElement {
    jsonRpc = new JsonRpc(this);
    configJsonRpc = new JsonRpc("devui-configuration");

    routerController = new RouterController(this);

    static styles = css`
        .bordered {
            border: 1px solid var(--lumo-contrast-20pct);
            border-radius: var(--lumo-border-radius-l);
            padding: var(--lumo-space-s) var(--lumo-space-m);
        }

        .dataSources {
            display: flex;
            flex-direction: column;
            gap: 20px;
            height: 100%;
            padding-left: 10px;
        }

        .chat-container {
            display: flex;
            height: 100%;
            flex-direction: column;
            padding-right: 20px;
        }

        .selector-section {
            display: flex;
            flex-direction: column;
            gap: 10px;
            margin-bottom: 15px;
        }

        .selector-row {
            display: flex;
            gap: 10px;
            flex-wrap: wrap;
            align-items: flex-end;
        }

        .pu-selector, .entity-selector {
            flex-grow: 1;
            min-width: 200px;
        }

        .entity-suggestions {
            display: flex;
            flex-direction: column;
            gap: 5px;
        }

        .suggestion-chips {
            display: flex;
            flex-wrap: wrap;
            gap: 5px;
            margin-bottom: 5px;
        }

        .chat-area {
            display: flex;
            flex-direction: column;
            flex: 1;
            overflow-y: auto;
            gap: 10px;
            padding: 15px;
            background-color: var(--lumo-contrast-5pct);
            border-radius: var(--lumo-border-radius-l);
            margin-bottom: 10px;
        }

        .message {
            max-width: 80%;
            padding: 10px 15px;
            border-radius: var(--lumo-border-radius-m);
            margin-bottom: 10px;
        }

        .user-message {
            align-self: flex-end;
            background-color: var(--lumo-primary-color-10pct);
            color: var(--lumo-body-text-color);
        }

        .system-message {
            align-self: flex-start;
            background-color: var(--lumo-contrast-10pct);
            color: var(--lumo-body-text-color);
        }

        .error-message {
            align-self: flex-start;
            background-color: var(--lumo-error-color-10pct);
            color: var(--lumo-error-text-color);
        }

        .chat-input {
            display: flex;
            gap: 10px;
            align-items: flex-end;
        }

        .chat-text-area {
            flex: 1;
        }

        .results-card {
            background-color: var(--lumo-base-color);
            padding: 10px;
            border-radius: var(--lumo-border-radius-m);
            margin-top: 5px;
            max-height: 300px;
            overflow-y: auto;
            border: 1px solid var(--lumo-contrast-20pct);
        }

        .result-item {
            margin-bottom: 5px;
            padding: 5px;
            border-bottom: 1px solid var(--lumo-contrast-10pct);
        }

        .result-key {
            font-weight: bold;
            margin-right: 5px;
        }

        .timestamp {
            font-size: var(--lumo-font-size-xs);
            color: var(--lumo-tertiary-text-color);
            margin-top: 4px;
        }

        .pagination {
            display: flex;
            justify-content: center;
            align-items: center;
            gap: 10px;
            margin-top: 5px;
        }

        .small-icon {
            height: var(--lumo-icon-size-s);
            width: var(--lumo-icon-size-s);
        }

        a, a:visited, a:focus, a:active {
            text-decoration: none;
            color: var(--lumo-body-text-color);
        }

        a:hover {
            text-decoration: none;
            color: var(--lumo-primary-text-color);
        }

        .hidden {
            display: none;
        }
    `;

    static properties = {
        _persistenceUnits: {state: true, type: Array},
        _selectedPersistenceUnit: {state: true},
        _entityTypes: {state: true, type: Array},
        _messages: {state: true, type: Array},
        _currentPageNumber: {state: true},
        _currentNumberOfPages: {state: true},
        _allowHql: {state: true},
        _loading: {state: true, type: Boolean}
    }

    constructor() {
        super();
        this._persistenceUnits = [];
        this._selectedPersistenceUnit = null;
        this._entityTypes = [];
        this._messages = [];
        this._currentPageNumber = 1;
        this._currentNumberOfPages = 1;
        this._pageSize = 15;
        this._loading = false;
        this._allowHql = false;
    }

    connectedCallback() {
        super.connectedCallback();

        const page = this.routerController.getCurrentPage();
        if (page && page.metadata) {
            this._allowHql = (page.metadata.allowHql === "true");
        } else {
            this._allowHql = false;
        }

        this.hotReload();
    }

    hotReload() {
        this._loading = true;
        this.jsonRpc.getInfo().then(response => {
            this._persistenceUnits = response.result.persistenceUnits;
            this._selectPersistenceUnit(this._persistenceUnits[0]);
            this._loading = false;
            this._addSystemMessage("Welcome to HQL Chat Console! Select a persistence unit and entity type, then enter your HQL queries.");
        }).catch(error => {
            console.error("Failed to fetch persistence units:", error);
            this._persistenceUnits = [];
            this._loading = false;
            notifier.showErrorMessage("Failed to fetch persistence units: " + error, "bottom-start", 30);
        });
    }

    render() {
        if (this._loading) {
            return this._renderFetchingProgress();
        } else if (this._persistenceUnits && this._persistenceUnits.length > 0) {
            return this._renderChatInterface();
        } else {
            return html`
                <p>No persistence units were found.
                    <vaadin-button @click="${this.hotReload}" theme="small">Check again</vaadin-button>
                </p>`;
        }
    }

    _renderFetchingProgress() {
        return html`
            <div style="color: var(--lumo-secondary-text-color);width: 95%;">
                <div>Fetching persistence units...</div>
                <vaadin-progress-bar indeterminate></vaadin-progress-bar>
            </div>`;
    }

    _renderChatInterface() {
        return html`
            <div class="dataSources">
                <div class="chat-container bordered">
                    <div class="selector-section">
                        <div class="selector-row">
                            <div class="pu-selector">
                                ${this._renderDatasourcesComboBox()}
                            </div>
                            <div class="entity-selector">
                                ${this._renderEntityTypes()}
                            </div>
                            ${!this._allowHql ? html`
                        <vaadin-button theme="small" @click="${this._handleAllowHqlChange}">
                            Allow HQL execution from here
                        </vaadin-button>` : ''}
                        </div>
                    </div>
                    <div class="chat-area" id="chat-area">
                        ${this._messages.map(msg => this._renderMessage(msg))}
                    </div>
                    ${this._allowHql ? html`
                        <div class="chat-input">
                            <vaadin-text-area class="chat-text-area" placeholder="Enter HQL query here..." id="hql-input"
                                              @keydown="${this._handleKeyDown}"></vaadin-text-area>
                            <vaadin-button theme="primary" @click="${this._sendQuery}">
                                <vaadin-icon icon="font-awesome-solid:paper-plane"></vaadin-icon>
                            </vaadin-button>
                        </div>` : ''}
                </div>
            </div>`;
    }

    _renderMessage(message) {
        if (message.type === 'user') {
            return html`
                <div class="message user-message">
                    <div><strong>Query:</strong> ${message.content}</div>
                    <div class="timestamp">${message.timestamp}</div>
                </div>`;
        } else if (message.type === 'error') {
            return html`
                <div class="message error-message">
                    <div><strong>Error:</strong> ${message.content}</div>
                    <div class="timestamp">${message.timestamp}</div>
                </div>`;
        } else if (message.type === 'result') {
            return html`
                <div class="message system-message">
                    ${message.message ? html`<div><strong>Message:</strong> ${message.message}</div>` : ''}
                    ${message.data ? html`
                        <div class="results-card">
                            ${this._renderResultsData(message.data)}
                        </div>
                        ${message.totalPages > 1 ? html`
                            <div class="pagination">
                                <vaadin-button theme="icon tertiary" ?disabled="${message.page === 1}"
                                               @click="${() => this._paginateResults(message.query, message.page - 1)}">
                                    <vaadin-icon icon="font-awesome-solid:chevron-left"></vaadin-icon>
                                </vaadin-button>
                                <span>Page ${message.page} of ${message.totalPages}</span>
                                <vaadin-button theme="icon tertiary" ?disabled="${message.page >= message.totalPages}"
                                               @click="${() => this._paginateResults(message.query, message.page + 1)}">
                                    <vaadin-icon icon="font-awesome-solid:chevron-right"></vaadin-icon>
                                </vaadin-button>
                            </div>` : ''}
                    ` : ''}
                    <div class="timestamp">${message.timestamp}</div>
                </div>`;
        } else {
            return html`
                <div class="message system-message">
                    <div>${message.content}</div>
                    <div class="timestamp">${message.timestamp}</div>
                </div>`;
        }
    }

    _renderResultsData(data) {
        if (!data || data.length === 0) {
            return html`<div>No results found.</div>`;
        }

        if (typeof data[0] === 'object') {
            return html`
                ${data.map((item, index) => html`
                    <div class="result-item">
                        ${Object.entries(item).map(([key, value]) => html`
                            <div>
                                <span class="result-key">${key}:</span>
                                <span>${this._formatValue(value)}</span>
                            </div>
                        `)}
                    </div>
                `)}
            `;
        } else {
            return html`
                ${data.map(value => html`
                    <div class="result-item">${this._formatValue(value)}</div>
                `)}
            `;
        }
    }

    _formatValue(value) {
        if (value === true) {
            return html`<vaadin-icon style="color: var(--lumo-contrast-50pct);" title="true" icon="font-awesome-regular:square-check"></vaadin-icon>`;
        } else if (value === false) {
            return html`<vaadin-icon style="color: var(--lumo-contrast-50pct);" title="false" icon="font-awesome-regular:square"></vaadin-icon>`;
        } else if (value) {
            if (typeof value === 'string' && (value.startsWith('http://') || value.startsWith('https://'))) {
                return html`<a href="${value}" target="_blank">${value}</a>`;
            } else {
                const s = typeof value === 'object' ? JSON.stringify(value) : value;
                return html`<span>${s}</span>`;
            }
        }
        return html`<span>null</span>`;
    }

    _renderDatasourcesComboBox() {
        return html`
            <vaadin-combo-box
                    label="Persistence Unit"
                    item-label-path="name"
                    item-value-path="name"
                    .items="${this._persistenceUnits}"
                    .value="${this._persistenceUnits[0]?.name || ''}"
                    @value-changed="${this._onPersistenceUnitChanged}"
                    .allowCustomValue="${false}"
            ></vaadin-combo-box>
        `;
    }

    _renderEntityTypes() {
        return html`
            <div class="entity-suggestions">
                <vaadin-combo-box
                        label="Entity Types"
                        .items="${this._entityTypes}"
                        placeholder="Select entity to use..."
                        @value-changed="${(e) => this._insertEntityName(e.detail.value)}"
                        clear-button-visible
                ></vaadin-combo-box>
            </div>
        `;
    }

    _insertEntityName(entityName) {
        if (entityName) {
            const input = this.shadowRoot.getElementById('hql-input');
            if (input) {
                input.value = `from ${entityName}`;
                input.focus();
            }
        }
    }

    _onPersistenceUnitChanged(event) {
        const selectedValue = event.detail.value;
        this._selectPersistenceUnit(this._persistenceUnits.find(unit => unit.name === selectedValue))
    }

    _selectPersistenceUnit(pu) {
        this._selectedPersistenceUnit = pu;

        // Update entity types when persistence unit changes
        if (pu) {
            if (pu.reactive) {
                this._addSystemMessage("Reactive persistence units are not supported in this console, please use a blocking one.");
            }
            else {
                // Extract entity types from the persistence unit
                this._entityTypes = pu.managedEntities ? pu.managedEntities.map(entity => entity.name) : [];
            }
        } else {
            this._entityTypes = [];
        }
    }

    _handleAllowHqlChange() {
        this.configJsonRpc.updateProperty({
            'name': '%dev.quarkus.hibernate-orm.dev-ui.allow-hql',
            'value': 'true'
        }).then(e => {
            this._allowHql = true;
            this._addSystemMessage("HQL execution is now enabled. You can start entering queries below.");
        });
    }

    _handleKeyDown(event) {
        if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
            event.preventDefault();
            this._sendQuery();
        }
    }

    _sendQuery() {
        const input = this.shadowRoot.getElementById('hql-input');
        const query = input.value.trim();

        if (!query) return;

        this._addUserMessage(query);
        input.value = '';

        this._executeHQL(query, 1);

        // Scroll to bottom
        setTimeout(() => {
            const chatArea = this.shadowRoot.getElementById('chat-area');
            chatArea.scrollTop = chatArea.scrollHeight;
        }, 100);
    }

    _executeHQL(hql, pageNumber) {
        if (!hql || !this._selectedPersistenceUnit) return;

        this._loading = true;

        this.jsonRpc.executeHQL({
            persistenceUnit: this._selectedPersistenceUnit.name,
            hql: hql,
            pageNumber: pageNumber,
            pageSize: this._pageSize
        }).then(jsonRpcResponse => {
            this._loading = false;

            const error = jsonRpcResponse.error && jsonRpcResponse.error.message ||
                (jsonRpcResponse.result && jsonRpcResponse.result.error);

            if (error) {
                this._addErrorMessage(error);
            } else if (jsonRpcResponse.result.message) {
                this._addResultMessage({
                    message: jsonRpcResponse.result.message,
                    data: null,
                    query: hql,
                    page: pageNumber,
                    totalPages: 1
                });
            } else {
                const result = jsonRpcResponse.result;
                const totalPages = Math.ceil(result.totalNumberOfElements / this._pageSize) || 1;

                this._addResultMessage({
                    message: null,
                    data: result.data,
                    query: hql,
                    page: pageNumber,
                    totalPages: totalPages
                });
            }

            // Scroll to bottom after results are displayed
            setTimeout(() => {
                const chatArea = this.shadowRoot.getElementById('chat-area');
                chatArea.scrollTop = chatArea.scrollHeight;
            }, 100);
        }).catch(error => {
            this._loading = false;
            this._addErrorMessage("Failed to execute query: " + error);
        });
    }

    _paginateResults(query, pageNumber) {
        this._executeHQL(query, pageNumber);
    }

    _addUserMessage(content) {
        this._messages = [...this._messages, {
            type: 'user',
            content,
            timestamp: this._getCurrentTime()
        }];
    }

    _addSystemMessage(content) {
        this._messages = [...this._messages, {
            type: 'system',
            content,
            timestamp: this._getCurrentTime()
        }];
    }

    _addErrorMessage(content) {
        this._messages = [...this._messages, {
            type: 'error',
            content,
            timestamp: this._getCurrentTime()
        }];
    }

    _addResultMessage(result) {
        this._messages = [...this._messages, {
            type: 'result',
            ...result,
            timestamp: this._getCurrentTime()
        }];
    }

    _getCurrentTime() {
        return new Date().toLocaleTimeString();
    }
}

customElements.define('hibernate-orm-hql-console', HibernateOrmHqlConsoleComponent);