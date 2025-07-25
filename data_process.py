import os
import json
from pathlib import Path
import re
from typing import List
from git import Commit, Repo
from tqdm import tqdm


def check_path(path: str):
    if path is None:
        return True
    if not path.endswith('.java'):
        return False
    if re.compile(r'[\u4e00-\u9fff]').search(path):
        return False
    return any(s in path for s in ['src/main/java', 'src/test/java'])

    
def generate(repo_name: str):
    path = Path('dataset') / (repo_name + '.json')
    if path.exists():
        return
    
    print("\033[32m" + repo_name + "\033[0m")

    repo = Repo(Path('projects') / repo_name)
    main = repo.branches[0]
    commits: List[Commit] = list(repo.iter_commits(main))
    commits.reverse()
    
    max_num = 3
    max_len = max_diff = 5
    i = 0
    tot = len(commits)
    
    pbar = tqdm(total=tot)
    res = []
    while i < tot:
        cnt = 1
        stop = False
        set1 = set()
        set2 = set()
        
        for j in range(i + 1, tot):
            commit = commits[j]
            par = commit.parents
            if cnt > max_len or len(par) != 1 or par[0] != commits[j - 1]:
                break

            diffs = par[0].diff(commit, create_patch=True, ignore_space_change=True)
            if len(diffs) > max_num:
                break
            
            has_mod = False
            tmp1, tmp2 = set(), set()
            for diff in diffs:
                patch = diff.diff.decode('utf-8', errors='ignore').split('\n')
                count = sum(len(s) >= 2 and s.startswith('@@') for s in patch)
                if count > max_diff:
                    stop = True
                    continue
                
                path1, path2 = diff.a_path, diff.b_path
                if check_path(path1) and check_path(path2):
                    has_mod = True
                    if path1 in set2 or path2 in set1:
                        stop = True
                        break
                    if path1:
                        tmp1.add(path1)
                    if path2:
                        tmp2.add(path2)
            
            if not has_mod or max(len(tmp1), len(tmp2)) > max_num:
                stop = True
            if stop: break
            
            cnt += 1
            set1 |= tmp1
            set2 |= tmp2

        # [i, i + cnt)
        if cnt > 2:
            res.append([c.hexsha[:10] for c in commits[i: i + cnt]])
        i += cnt
        pbar.update(cnt)
    
    pbar.close()

    with open(path, 'w') as output:
        json.dump(res, output, indent=4)
        print(f'Collect {len(res)} composite commits')


if __name__ == '__main__':
    # generate('javaparser')
    with open('repos.txt') as file:
        data = file.read().split()
    for repo_name in data:
        generate(repo_name)
    