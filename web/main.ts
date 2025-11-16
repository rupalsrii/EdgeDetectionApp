const preview = document.getElementById("preview") as HTMLImageElement;
const grayBtn = document.getElementById("grayBtn") as HTMLButtonElement;
const edgeBtn = document.getElementById("edgeBtn") as HTMLButtonElement;

grayBtn.onclick = () => {
    preview.src = "images/sample_gray.jpg";
};

edgeBtn.onclick = () => {
    preview.src = "images/sample_edges.jpg";
};
