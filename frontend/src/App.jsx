import React, { useState, useEffect } from 'react';
import './App.css';

const API_BASE = 'http://localhost:9000';

function App() {
  const [providers, setProviders] = useState([]);
  const [selectedProvider, setSelectedProvider] = useState('');
  const [circumstance, setCircumstance] = useState('');
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetch(`${API_BASE}/providers`)
      .then(r => r.json())
      .then(setProviders)
      .catch(() => setError('Failed to load providers'));
  }, []);

  const handleValidate = async () => {
    setLoading(true);
    setError(null);
    setResult(null);

    try {
      const response = await fetch(`${API_BASE}/data`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ValidateCircumstance: {
            providerName: selectedProvider,
            circumstanceChange: circumstance,
            userRequestId: `${Date.now()}-${Math.random()}`
          }
        })
      });

      if (!response.ok) throw new Error('Validation failed');

      const validationId = `${selectedProvider}-${Date.now()}`;

      let attempts = 0;
      while (attempts < 10) {
        await new Promise(r => setTimeout(r, 2000));

        const resultResponse = await fetch(`${API_BASE}/validations/${validationId}`);
        if (resultResponse.ok) {
          const data = await resultResponse.json();
          setResult(data);
          setLoading(false);
          return;
        }
        attempts++;
      }

      throw new Error('Validation timeout');
    } catch (e) {
      setError(e.message);
      setLoading(false);
    }
  };

  return (
    <div className="App">
      <h1>Insurance Change Validator</h1>
      <div className="form">
        <label>Select Insurance Provider:</label>
        <select
          value={selectedProvider}
          onChange={e => setSelectedProvider(e.target.value)}
          disabled={loading}
        >
          <option value="">Choose provider...</option>
          {providers.map(p => (
            <option key={p} value={p}>{p}</option>
          ))}
        </select>

        <label>Describe Your Change in Circumstances:</label>
        <textarea
          value={circumstance}
          onChange={e => setCircumstance(e.target.value)}
          placeholder="e.g., 'Im custom upgrading my exhaust'"
          rows={4}
          disabled={loading}
        />

        <button
          onClick={handleValidate}
          disabled={!selectedProvider || !circumstance || loading}
        >
          {loading ? 'Analyzing...' : 'Check Risk Level'}
        </button>
      </div>

      {error && <div className="error">{error}</div>}

      {result && (
        <div className={`result risk-${result.riskLevel.toLowerCase()}`}>
          <h2>Risk Level: {result.riskLevel}</h2>
          <p>{result.justification}</p>
          {result.matchedCategories?.length > 0 && (
            <div>
              <strong>Affected Areas:</strong>
              <ul>
                {result.matchedCategories.map(cat => <li key={cat}>{cat}</li>)}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default App;
