const express = require("express");
const _ = require("lodash");

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());

app.get("/", (req, res) => {
	res.json({
		application: "Jenkins Security POC",
		status: "running"
	});
});

app.get("/health", (req, res) => {
	res.json({
		status: "UP"
	});
});

app.get("/user/:name", (req, res) => {
	const name = req.params.name;

	const user = {
		name: name,
		role: "developer"
	};

	res.json(user);
});

app.get("/calculate", (req, res) => {
	const values = [10, 20, 30, 40];

	const total = _.sum(values);

	res.json({
		values,
		total
	});
});

app.listen(PORT, () => {
	console.log(`Application running on port ${PORT}`);
});
