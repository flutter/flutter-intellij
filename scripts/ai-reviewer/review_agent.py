import os
import sys
from github import Github
import google.generativeai as genai

# 1. Setup & Authentication
GITHUB_TOKEN = os.getenv('GITHUB_TOKEN')
GEMINI_API_KEY = os.getenv('GEMINI_API_KEY')
PR_NUMBER = int(sys.argv[1]) # Passed from the workflow file

genai.configure(api_key=GEMINI_API_KEY)
gh = Github(GITHUB_TOKEN)
repo = gh.get_repo("flutter/flutter-intellij") # Update to your repo path
pr = repo.get_pull(PR_NUMBER)

def get_agent_review():
    # 2. Load your ai-standards.md
    with open("docs/ai-standards.md", "r") as f:
        standards = f.read()

    # 3. Get the PR Diff (The code changes)
    # We fetch the diff as a string to feed to the AI
    comparison = repo.compare(pr.base.sha, pr.head.sha)
    diff_content = ""
    for file in comparison.files:
        diff_content += f"File: {file.filename}\n{file.patch}\n\n"

    # 4. Construct the Prompt
    prompt = f"""
    You are a Senior Google Software Engineer. Review the following PR for the Flutter IntelliJ plugin.
    
    ### STANDARDS TO FOLLOW:
    {standards}
    
    ### PR DIFF:
    {diff_content}
    
    ### INSTRUCTIONS:
    1. Identify violations of the standards.
    2. Provide a summary of the review.
    3. End with "ACTION: READY_FOR_HUMAN_REVIEW" and the username of the person to assign.
    """

    model = genai.GenerativeModel('gemini-1.5-pro')
    response = model.generate_content(prompt)
    return response.text

def apply_feedback(review_text):
    # 5. Post the review to GitHub
    pr.create_issue_comment(review_text)
    
    # 6. Reassign if the agent is finished
    if "ACTION: READY_FOR_HUMAN_REVIEW" in review_text:
        # Example: Reassigning to you
        pr.add_to_assignees("your-github-username")
        print("PR reassigned to human reviewer.")

if __name__ == "__main__":
    review = get_agent_review()
    apply_feedback(review)