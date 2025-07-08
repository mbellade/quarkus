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
            align-items: center;
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

        .spinner {
            animation: spin 1s linear infinite;
            font-size: var(--lumo-font-size-xl);
            color: var(--lumo-primary-color);
        }

        @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }

        .expandable-json {
            cursor: pointer;
            display: flex;
            align-items: center;
            gap: 5px;
        }

        .expand-collapse-btn {
            display: inline-flex;
            justify-content: center;
            align-items: center;
            width: 20px;
            height: 20px;
            border-radius: 50%;
            background: var(--lumo-contrast-10pct);
            border: none;
            cursor: pointer;
            margin-right: 5px;
        }

        .nested-table {
            margin-left: 20px;
            margin-top: 8px;
            max-width: 100%;
            overflow-x: auto;
            border-left: 2px solid var(--lumo-contrast-10pct);
            padding-left: 10px;
        }

        .table-container {
            width: 100%;
            overflow-x: auto;
            margin: 10px 0;
        }

        .table-container table {
            width: 100%;
            border-collapse: collapse;
            border-spacing: 0;
        }

        .table-container th {
            text-align: left;
            padding: 8px 12px;
            font-weight: bold;
            background-color: var(--lumo-contrast-5pct);
            border-bottom: 1px solid var(--lumo-contrast-20pct);
            white-space: nowrap;
        }

        .table-container td {
            padding: 8px 12px;
            border-bottom: 1px solid var(--lumo-contrast-10pct);
            vertical-align: top;
        }

        /* Alternate row colors for better readability */
        .table-container tbody tr:nth-child(even) {
            background-color: var(--lumo-contrast-5pct);
        }

        .nested-table table {
            width: 100%;
            border-collapse: collapse;
        }

        .nested-table th {
            text-align: left;
            padding: 4px 8px;
            font-weight: bold;
            background-color: var(--lumo-contrast-5pct);
            border-bottom: 1px solid var(--lumo-contrast-20pct);
        }

        .nested-table td {
            padding: 4px 8px;
            border-bottom: 1px solid var(--lumo-contrast-10pct);
            vertical-align: top;
        }

        .json-preview {
            color: var(--lumo-secondary-text-color);
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
                                              @keydown="${this._handleKeyDown}" style="height: 40px;"></vaadin-text-area>
                            <vaadin-button theme="contrast" @click="${this._clearInput}">
                                <vaadin-icon icon="font-awesome-solid:trash"></vaadin-icon>
                            </vaadin-button>
                            <vaadin-button theme="primary" @click="${this._sendQuery}"
                                           ?disabled="${this._selectedPersistenceUnit?.reactive}">
                                <vaadin-icon icon="font-awesome-solid:play"></vaadin-icon>
                            </vaadin-button>
                        </div>` : ''}
                </div>
            </div>`;
    }

    _clearInput() {
        const input = this.renderRoot.querySelector('#hql-input');
        if (input) {
            input.value = '';
            input.focus();
        }
    }

    _renderMessage(message) {
        if (message.type === 'loading') {
            return html`
            <div class="message system-message">
                <div style="display: flex; justify-content: center; align-items: center;">
                    <vaadin-icon icon="font-awesome-solid:circle-notch" class="spinner"></vaadin-icon>
                </div>
            </div>`;
        } else if (message.type === 'user') {
            return html`
            <div class="message user-message">
                <div><strong>Query:</strong> ${message.content}</div>
            </div>`;
        } else if (message.type === 'error') {
            return html`
            <div class="message error-message">
                <div><strong>Error:</strong> ${message.content}</div>
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
            </div>`;
        } else {
            return html`
            <div class="message system-message">
                <div>${message.content}</div>
            </div>`;
        }
    }

    _renderResultsData(data) {
        if (!data || data.length === 0) {
            return html`<div>No results found.</div>`;
        }

        return this._renderArrayTable(data, false);
    }

    _formatValue(value) {
        if (value === true) {
            return html`<vaadin-icon style="color: var(--lumo-contrast-50pct);" title="true" icon="font-awesome-regular:square-check"></vaadin-icon>`;
        } else if (value === false) {
            return html`<vaadin-icon style="color: var(--lumo-contrast-50pct);" title="false" icon="font-awesome-regular:square"></vaadin-icon>`;
        } else if (value === null || value === undefined) {
            return html`<span>null</span>`;
        } else if (typeof value === 'object') {
            return this._renderExpandableJson(value);
        } else if (typeof value === 'string' && (value.startsWith('http://') || value.startsWith('https://'))) {
            return html`<a href="${value}" target="_blank">${value}</a>`;
        } else {
            return html`<span>${value}</span>`;
        }
    }

    _renderExpandableJson(value) {
        const isArray = Array.isArray(value);
        const preview = isArray
            ? `Array[${value.length}]`
            : `Object{${Object.keys(value).length} properties}`;

        // Generate a unique ID for this expandable section
        const expandId = `expand-${Math.random().toString(36).substring(2, 11)}`;

        return html`
            <div class="expandable-json">
                <button class="expand-collapse-btn" @click="${(e) => this._toggleExpand(e, expandId)}">+</button>
                <span class="json-preview">${preview}</span>
                <div id="${expandId}" class="nested-table" style="display: none">
                    ${isArray
                            ? this._renderArrayTable(value, true) // true means this is nested
                            : this._renderObjectTable(value)}
                </div>
            </div>
        `;
    }

    _toggleExpand(e, expandId) {
        e.stopPropagation();
        const element = this.renderRoot.querySelector(`#${expandId}`);
        const button = e.target;

        if (element.style.display === 'none') {
            element.style.display = 'block';
            button.textContent = '-';
        } else {
            element.style.display = 'none';
            button.textContent = '+';
        }
    }

    _renderArrayTable(array, isNested = false) {
        if (array.length === 0) {
            return html`<div style="font-style: italic; color: var(--lumo-tertiary-text-color);">[empty array]</div>`;
        }

        // Check if we need a complex table (objects) or a simple list
        const isComplexArray = array.some(item => typeof item === 'object' && item !== null);

        // Use appropriate container class based on whether this is nested
        const containerClass = isNested ? "table-container" : "table-container";

        if (isComplexArray) {
            // Get all possible keys from objects in the array
            const keys = new Set();
            array.forEach(item => {
                if (typeof item === 'object' && item !== null) {
                    Object.keys(item).forEach(key => keys.add(key));
                }
            });

            return html`
                <div class="${containerClass}">
                    <table>
                        <thead>
                        <tr>
                            ${[...keys].map(key => html`<th>${key}</th>`)}
                        </tr>
                        </thead>
                        <tbody>
                        ${array.map(item => {
                            if (typeof item === 'object' && item !== null) {
                                return html`
                                    <tr>
                                        ${[...keys].map(key => html`
                                            <td>${this._formatValue(item[key])}</td>
                                        `)}
                                    </tr>
                                `;
                            }
                            return '';
                        })}
                        </tbody>
                    </table>
                </div>
            `;
        } else {
            // Simple table for primitive values
            return html`
                <div class="${containerClass}">
                    <table>
                        <thead>
                        <tr>
                            <th>Index</th>
                            <th>Value</th>
                        </tr>
                        </thead>
                        <tbody>
                        ${array.map((item, index) => html`
                            <tr>
                                <td style="width: 50px;">${index}</td>
                                <td>${this._formatValue(item)}</td>
                            </tr>
                        `)}
                        </tbody>
                    </table>
                </div>
            `;
        }
    }

    _renderObjectTable(obj) {
        return html`
        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>Property</th>
                        <th>Value</th>
                    </tr>
                </thead>
                <tbody>
                    ${Object.entries(obj).map(([key, value]) => html`
                        <tr>
                            <td style="width: 30%;">${key}</td>
                            <td>${this._formatValue(value)}</td>
                        </tr>
                    `)}
                </tbody>
            </table>
        </div>
    `;
    }

    _capitalize(string) {
        return string.charAt(0).toUpperCase() + string.slice(1);
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
            const input = this.renderRoot.querySelector('#hql-input');
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
        // Don't execute queries for reactive persistence units
        if (this._selectedPersistenceUnit?.reactive) {
            return;
        }

        if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
            event.preventDefault();
            this._sendQuery();
        }
    }

    _sendQuery() {
        const input = this.renderRoot.querySelector('#hql-input');
        const query = input.value.trim();

        if (!query) return;

        this._addUserMessage(query);

        this._executeHQL(query, 1);
    }

    _executeHQL(hql, pageNumber) {
        if (!hql || !this._selectedPersistenceUnit) return;

        // Create a loading message instead of setting global loading state
        const loadingMessageIndex = this._messages.length;
        this._messages = [...this._messages, {
            type: 'loading',
            query: hql
        }];

        this.jsonRpc.executeHQL({
            persistenceUnit: this._selectedPersistenceUnit.name,
            hql: hql,
            pageNumber: pageNumber,
            pageSize: this._pageSize
        }).then(jsonRpcResponse => {
            // Clone the messages array to modify it
            const updatedMessages = [...this._messages];

            const error = jsonRpcResponse.error && jsonRpcResponse.error.message ||
                (jsonRpcResponse.result && jsonRpcResponse.result.error);

            if (error) {
                // Replace loading message with error
                updatedMessages[loadingMessageIndex] = {
                    type: 'error',
                    content: error
                };
            } else if (jsonRpcResponse.result.message) {
                // Replace loading message with result message
                updatedMessages[loadingMessageIndex] = {
                    type: 'result',
                    message: jsonRpcResponse.result.message,
                    data: null,
                    query: hql,
                    page: pageNumber,
                    totalPages: 1
                };
            } else {
                const result = jsonRpcResponse.result;
                const totalPages = Math.ceil(result.totalNumberOfElements / this._pageSize) || 1;

                // Replace loading message with result data
                updatedMessages[loadingMessageIndex] = {
                    type: 'result',
                    message: null,
                    data: result.data,
                    query: hql,
                    page: pageNumber,
                    totalPages: totalPages
                };
            }

            this._messages = updatedMessages;

            // Scroll to bottom after results are displayed
            setTimeout(() => {
                const chatArea = this.renderRoot.querySelector('#chat-area');
                chatArea.scrollTop = chatArea.scrollHeight;
            }, 100);
        }).catch(error => {
            // Replace loading message with error on exception
            const updatedMessages = [...this._messages];
            updatedMessages[loadingMessageIndex] = {
                type: 'error',
                content: "Failed to execute query: " + error
            };
            this._messages = updatedMessages;
        });
    }

    _paginateResults(query, pageNumber) {
        this._executeHQL(query, pageNumber);
    }

    _addUserMessage(content) {
        this._messages = [...this._messages, {
            type: 'user',
            content
        }];
    }

    _addSystemMessage(content) {
        this._messages = [...this._messages, {
            type: 'system',
            content
        }];
    }

    _addErrorMessage(content) {
        this._messages = [...this._messages, {
            type: 'error',
            content
        }];
    }

    _addResultMessage(result) {
        this._messages = [...this._messages, {
            type: 'result',
            ...result
        }];
    }

    _getCurrentTime() {
        return new Date().toLocaleTimeString();
    }
}

customElements.define('hibernate-orm-hql-console', HibernateOrmHqlConsoleComponent);